package com.hwx.ambariapilib.upgrade;

import com.hwx.ambariapilib.AmbariItems;
import com.hwx.ambariapilib.cluster.StackVersion;
import com.hwx.ambariapilib.json.cluster.ClusterJson;
import com.hwx.ambariapilib.json.stack.RepositoryVersionJson;
import com.hwx.ambariapilib.json.stack.StackVersionListJson;
import com.hwx.ambariapilib.json.upgrade.*;
import com.hwx.ambariapilib.service.Service;
import com.hwx.ambariapilib.service.ServiceComponent;
import com.hwx.clientlib.http.HTTPBody;
import com.hwx.clientlib.http.HTTPMethods;
import com.hwx.clientlib.http.HTTPRequest;
import com.hwx.clientlib.http.HTTPResponse;
import com.hwx.utils.FileUtils;
import com.hwx.utils.LinuxCommandExecutor;
import com.hwx.utils.WaitUtil;
import com.hwx.utils.validation.ValidationUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by vsharma on 11/5/15.
 */
public class StackUpgrade extends AmbariItems {

    private ClusterJson clusterJson;
    private int lastUpgradeRequestID=-1;

    public StackUpgrade(ClusterJson clusterJson) {
        this.clusterJson=clusterJson;
    }

    public enum UPGRADESTATUS {
        COMPLETED,
        PENDING,
        HOLDING,
        HOLDING_FAILED,
        HOLDING_TIMEDOUT,
        SKIPPED_FAILED,
        FAILED,
        IN_PROGRESS
    }

    public int getLastUpgradeRequestID() throws Exception {
        if(this.lastUpgradeRequestID == -1)
            setLastUpgradeRequestID();

        return this.lastUpgradeRequestID;
    }

    /**
     * Shoudl be called before downgrade starts, as downgrade would initiate a new Request ID
     */
    private void resetLastUpgradeRequestID() {
        this.lastUpgradeRequestID = -1;
    }

    //Register a new version
    public void registerNewVersion(String stackName, String stackVersion, String buildNumber, String operatingSystem, String hdpBaseUrl, String hdpUtilsBaseUrl) throws Exception {
        Map<String,String> map = new HashMap<String,String>();
        map.put("{STACKNAME}",stackName);
        map.put("{STACKVERSION}",stackVersion);
        map.put("{BUILDNUMBER}",buildNumber);
        map.put("{OPERATINGSYSTEM}",operatingSystem);
        map.put("{HDPBASEURL}",hdpBaseUrl);
        map.put("{HDPUTILBASEURL}", hdpUtilsBaseUrl);


        String body = FileUtils.getJsonAsString("UpgradeVersion.json",map);
        HTTPRequest req = new HTTPRequest(HTTPMethods.POST, "/stacks/HDP/versions/" + stackVersion + "/repository_versions/");
        req.setBody(new HTTPBody(body));
        HTTPResponse resp = rc.sendHTTPRequest(req);

        if(ValidationUtils.validateResponseCode(resp, 201))
            logger.logInfo("Register Version successful");
        else{
            logger.logError(resp.getBody().getBodyText());
            logger.logError("Error during register version");
            throw new Exception("Error during register version");
        }
    }

    //Submit the install package request
    public boolean submitInstallPackageRequest(String stackName, String stackVersion, String buildNumber) throws Exception {
        // TODO - Run "yum clean all" or OS specific equivalent on all hosts
        Map<String,String> map = new HashMap<String,String>();
        map.put("{STACKNAME}",stackName);
        map.put("{STACKVERSION}",stackVersion);
        map.put("{BUILDNUMBER}", buildNumber);
        // map.put("{REPO_VERSION}",getInstalledStackVersion());

        String body = FileUtils.getJsonAsString("InstallPackageRequest.json", map);
        HTTPRequest req = new HTTPRequest(HTTPMethods.POST, clusterJson.getHref()+"/stack_versions");
        req.setBody(new HTTPBody(body));
        HTTPResponse resp = rc.sendHTTPRequest(req);

        logger.logInfo("Started Package Installation on all hosts");

        //Response code must be 202 with request id to track the progress.
        if(ValidationUtils.validateResponseCode(resp,202)){
            //Get the request id
            UpgradeRequestJson upgradeRequestJson = gson.fromJson(resp.getBody().getBodyText(), UpgradeRequestJson.class);
            int requestId = upgradeRequestJson.getRequests().getId();

            //Monitor the request status and wait till status is completed
            //ToDo Hack for clustername passing
            WaitUtil.waitForRequestToBeCompleted(clusterJson.getHref(), requestId);
            logger.logInfo("Package Installation successful on all hosts");
            return true;
        }
        else{
            logger.logError(resp.getBody().getBodyText());
            return false;
        }

    }

    /**
     * Submits EU
     * @param upgradeParams
     * @throws Exception
     */
    public void submitExpressUpgrade(UpgradeParams upgradeParams) throws Exception {
        logger.logInfo("Started Express Upgrade...");
        submitUpgrade("NON_ROLLING", upgradeParams.getSkipServiceCheckFailure(), upgradeParams.getSkipSlaveFailures(), upgradeParams.getSkipPrerequisiteChecks(),
                upgradeParams.getFailOnCheckWarnings(), upgradeParams.getSkipManualVerification());

    }

    /**
     * Submits RU
     * @param upgradeParams
     * @throws Exception
     */
    public void submitRollingUpgrade(UpgradeParams upgradeParams) throws Exception {

        submitUpgrade("ROLLING", upgradeParams.getSkipServiceCheckFailure(), upgradeParams.getSkipSlaveFailures(), upgradeParams.getSkipPrerequisiteChecks(),
                upgradeParams.getFailOnCheckWarnings(), upgradeParams.getSkipManualVerification());

    }

    /**
     * Submits EU and waits till finalize step
     * @param upgradeParams
     * @throws Exception
     */
    public void submitExpressUpgradeTillFinalize(UpgradeParams upgradeParams) throws Exception {
        logger.logInfo("Started Express Upgrade...");
        submitExpressUpgradeTillSpecificManualStep(upgradeParams, "ready to finalize");
        logger.logInfo("Express Upgrade completed till finalize phase");
    }

    /**
     * Submits EU and waits till the specified manual text, so that next action can be taken - generally useful for Failure-Recvoery tests
     * @param upgradeParams
     * @param matchingTextForManualStep - Match the text in EU at which manual intervention is required
     * @throws Exception
     */
    public void submitExpressUpgradeTillSpecificManualStep(UpgradeParams upgradeParams, String matchingTextForManualStep) throws Exception {
        submitExpressUpgrade(upgradeParams);
        String status = getLastUpgradeStatus().getUpgrade().getRequest_status();

        while (true) {
            if (status.equals(UPGRADESTATUS.HOLDING.toString())) {
                if (getLastPendingUpgradeItem().getUpgradeItem().getText().contains(matchingTextForManualStep)) {
                    logger.logInfo(String.format("Detected that the Upgrade process is at %s stage", matchingTextForManualStep));
                    return;
                }
                else
                    proceedUpgradeAfterManualVerification();
            }
            else if (hasUpgradeFailed(status))
                throw new Exception("Error during Upgrade operation");
            else {
                logger.logInfo("Current upgrade status: " + status);
                logger.logInfo(String.format("Waiting for Upgrade to reach '%s' stage...", matchingTextForManualStep));
                WaitUtil.waitForFixedInterval(30);
            }
            status = getLastUpgradeStatus().getUpgrade().getRequest_status();
            hasUpgradeFailed();
        }
    }

    /**
     * Starts upgrade - private method, called by other upgrade wrappers
     * @param upgradeType - ROLLING or Express aka NON_ROLLING
     * @param skipServiceCheckFailure
     * @param skipSlaveFailures
     * @param skipPrerequisiteChecks
     * @param failOnCheckWarnings
     * @param skipManualVerification
     * @throws Exception
     */
    private void submitUpgrade(String upgradeType,String skipServiceCheckFailure, String skipSlaveFailures, String skipPrerequisiteChecks, String failOnCheckWarnings, String skipManualVerification) throws Exception {

        Map<String,String> upgradeParameters = new HashMap<String,String>();

        upgradeParameters.put("{UPGRADE_TYPE}", upgradeType);
        upgradeParameters.put("{REPO_VERSION}", getInstalledStackVersion());
        upgradeParameters.put("{DIRECTION}", "upgrade");

        upgradeParameters.put("{SKIP_SERVICE_CHECK_FAILURE}", skipServiceCheckFailure);
        upgradeParameters.put("{SKIP_SLAVE_FAILURE}", skipSlaveFailures);
        upgradeParameters.put("{SKIP_PREREQUISITE_CHECKS}", skipPrerequisiteChecks);
        upgradeParameters.put("{FAIL_ON_CHECK_WARNINGS}", failOnCheckWarnings);
        upgradeParameters.put("{SKIP_MANUAL_VERIFICATION}", skipManualVerification);

        String body = FileUtils.getJsonAsString("UpgradeRequest.json",upgradeParameters);
        HTTPRequest req = new HTTPRequest(HTTPMethods.POST, clusterJson.getHref()+"/upgrades");
        req.setBody(new HTTPBody(body));
        HTTPResponse resp = rc.sendHTTPRequest(req);
        logger.logInfo(resp.getBody().getBodyText());
    }

    /**
     * Checks if the status of upgrade is Failed ot not
     * @param status - varargs - if blank, status is determined first; else used as the first element that is passed
     * @return
     * @throws Exception
     */
    private boolean hasUpgradeFailed(String... status) throws Exception {
        String currentUpgradeStatus;
        if(status.length == 0)
            currentUpgradeStatus = getLastUpgradeStatus().getUpgrade().getRequest_status();
        else
            currentUpgradeStatus = status[0];

        if(currentUpgradeStatus.contains(UPGRADESTATUS.FAILED.toString()) || currentUpgradeStatus.contains(UPGRADESTATUS.HOLDING_TIMEDOUT.toString())) {
            getLastFailedUpgradeItem();
            return true;
        }
        return false;
    }

    /**
     * Wrapper for downgrade after EU
     * @throws Exception
     */
    public void submitDowngradeAfterExpressUpgrade() throws Exception {
        submitDowngrade("NON_ROLLING");

    }

    /**
     * Submit Downgrade - should be called after EU
     * @param upgradeType
     * @throws Exception
     */
    public void submitDowngrade(String upgradeType) throws Exception {
        abortCurrentUpgrade();
        resetLastUpgradeRequestID();    // Called because if downgrade is initiated a new request ID would be created by the API
        Map<String,String> downgradeParameters = new HashMap<String,String>();
        downgradeParameters.put("{FROM_VERSION}", getInstalledStackVersion());
        downgradeParameters.put("{REPO_VERSION}", getCurrentStackVersion());
        downgradeParameters.put("{UPGRADE_TYPE}", upgradeType);

        String body = FileUtils.getJsonAsString("DowngradeRequest.json",downgradeParameters);
        HTTPRequest req = new HTTPRequest(HTTPMethods.POST, clusterJson.getHref()+"/upgrades");
        req.setBody(new HTTPBody(body));
        HTTPResponse resp = rc.sendHTTPRequest(req);
        logger.logInfo(resp.getBody().getBodyText());
    }

    /**
     * Aborts upgrade, called just before downgrade
     * @throws Exception
     */
    private void abortCurrentUpgrade() throws Exception {
        changeUpgradeState("ABORTED");
        logger.logInfo("Upgrade aborted");
    }

    /**
     * Checks if upgrade is done; keeps polling the
     * @return
     * @throws Exception
     */
    private boolean isUpgradeComplete() throws Exception {
        String status = getLastUpgradeStatus().getUpgrade().getRequest_status();
        String message = "";
        if (status.contains(UPGRADESTATUS.COMPLETED.toString()))
            return true;
        else if(status.equals(UPGRADESTATUS.HOLDING.toString())) {
            message = String.format("Upgrade requires manual intervention. Current upgrade status: %s", status);
            logger.logInfo(message);
            logger.logInfo("Handling manual intervention step...");
            proceedUpgradeAfterManualVerification();
            logger.logInfo("Handling manual intervention step - DONE");
        }
        else if(status.contains(UPGRADESTATUS.PENDING.toString())) {
            message = String.format("Upgrade is queued. Current upgrade status: %s", status);
            logger.logInfo(message);
        }
        else if(status.contains(UPGRADESTATUS.IN_PROGRESS.toString())) {
            message = String.format("Upgrade is in progress. Current upgrade status: %s", status);
            logger.logInfo(message);
        }
        else if(hasUpgradeFailed(status)) {
            message = String.format("Error during Upgrade Operation. Upgrade failed with status: %s", status);
            logger.logError(message);
            getLastFailedUpgradeItem();
            throw new Exception(message);
        }

        return false;
    }

    /**
     * Waits for Upgrade/Dwongrade operation to complete
     * @param isUpgrade
     * @throws Exception
     */
    public void waitforOperationCompletion(boolean isUpgrade) throws Exception {

        while(!isUpgradeComplete()) {
            if(isUpgrade)
                logger.logInfo("Waiting for Express Upgrade to complete...");
            else
                logger.logInfo("Waiting for Express Downgrade to complete...");

            WaitUtil.waitForFixedInterval(30);
        }
        if(isUpgrade)
            logger.logInfo("Express Upgrade completed");
        else
            logger.logInfo("Express Downgrade completed");
    }

    /**
     * Determinxes last upgrade request ID; used for tracking EU status
     * @throws Exception
     */
    private void setLastUpgradeRequestID() throws Exception {
        HTTPRequest req = new HTTPRequest(HTTPMethods.GET, clusterJson.getHref()+"/upgrades/");
        HTTPResponse resp = rc.sendHTTPRequest(req);

        UpgradeEntryJson upgradeEntryJson = gson.fromJson(resp.getBody().getBodyText(), UpgradeEntryJson.class);
        ItemsJson[] items = upgradeEntryJson.getItems();
        ItemsJson lastUpgradeItem = null;

        if(items.length > 0)
            lastUpgradeItem = items[items.length-1];
        else
            throw new Exception("Unable to retrieve Upgrade Status. The API output indicates that upgrade was not started");

        this.lastUpgradeRequestID = lastUpgradeItem.getUpgrade().getRequest_id();
    }

    /**
     * Submits API call and returns response object
     * @param operation
     * @param uri
     * @return
     * @throws Exception
     */
    private HTTPResponse getAPIResponse(HTTPMethods operation, String uri) throws Exception {
        HTTPRequest req = new HTTPRequest(operation, uri);
        return rc.sendHTTPRequest(req);

    }

    /**
     * Gets status of most recent EU, this is used by other methods
     * @return
     * @throws Exception
     */
    private UpgradeStatusJson getLastUpgradeStatus() throws Exception {
        HTTPResponse resp = getAPIResponse(HTTPMethods.GET, clusterJson.getHref() + "/upgrades/" + getLastUpgradeRequestID());

        return gson.fromJson(resp.getBody().getBodyText(), UpgradeStatusJson.class);
    }

    /**
     * Returns info about latest failed Upgrade Item
     * @return
     * @throws Exception
     */
    private UpgradeItemsJson getLastFailedUpgradeItem() throws Exception {
        UpgradeItemsJson upgradeItemsJson = getUpgradeItem(UPGRADESTATUS.FAILED);

        logger.logInfo(upgradeItemsJson.getUpgradeItem().getStatus());
        logger.logInfo(upgradeItemsJson.getHref());
        logger.logInfo(upgradeItemsJson.getUpgradeItem().getText());
        printFailedUpgradeTasks(upgradeItemsJson.getTasks());
        return upgradeItemsJson;
    }

    /**
     * Provides a list of all failed tasks for EU
     * @return
     * @throws Exception
     */
    public List<TasksJson> getAllFailedTasks() throws Exception {
        HTTPResponse resp = getAPIResponse(HTTPMethods.GET, clusterJson.getHref() + "/upgrades/" + getLastUpgradeRequestID());

        List<TasksJson> tasks = new ArrayList<TasksJson>();

        UpgradeStatusJson upgradeStatusJson = gson.fromJson(resp.getBody().getBodyText(), UpgradeStatusJson.class);

        UpgradeGroupsJson[] upgradeGroupsJson = upgradeStatusJson.getUpgrade_groups();

        for (int i = 0; i < upgradeGroupsJson.length; i++) {
            resp = getAPIResponse(HTTPMethods.GET,upgradeGroupsJson[i].getHref());
            UpgradeGroupsJson upgradeGroupJson = gson.fromJson(resp.getBody().getBodyText(), UpgradeGroupsJson.class);
            UpgradeItemsJson[] upgradeItemsJson = upgradeGroupJson.getUpgrade_items();

            for (int j = 0; j < upgradeItemsJson.length; j++) {
                resp = getAPIResponse(HTTPMethods.GET,upgradeItemsJson[j].getHref());
                UpgradeItemsJson upgradeItemJson = gson.fromJson(resp.getBody().getBodyText(), UpgradeItemsJson.class);

                for (int k=0;k < upgradeItemJson.getTasks().length ; k++) {
                    resp = getAPIResponse(HTTPMethods.GET, upgradeItemJson.getTasks()[k].getHref());
                    TasksJson taskJson = gson.fromJson(resp.getBody().getBodyText(), TasksJson.class);
                    if(taskJson.getTask().getStatus().contains(UPGRADESTATUS.FAILED.toString()))
                        tasks.add(taskJson);
                }
            }
        }
        return tasks;
    }

    /**
     * Prints fails tasks info
     * @param tasksJson
     * @throws Exception
     */
    public void printFailedUpgradeTasks(TasksJson[] tasksJson) throws Exception {

        for(int i=0 ; i < tasksJson.length ; i++) {
            HTTPResponse resp = getAPIResponse(HTTPMethods.GET, tasksJson[i].getHref());
            logger.logInfo("===========Upgrade Tasks Info==========================");
            logger.logInfo(resp.getBody().getBodyText());
            logger.logInfo("=====================================");
        }

    }

    /**
     * Prints the complete output of EU process; useful for debugging any failures that may have happened
     * @throws Exception
     */
    public void printEntireUpgradeOutput() throws Exception {
        logger.logDebug("==== Printing complete API output of Upgrade/Downgrade operation ====");
        HTTPResponse resp = getAPIResponse(HTTPMethods.GET, clusterJson.getHref() + "/upgrades/" + getLastUpgradeRequestID());
        logger.logDebug(resp.getBody().getBodyText());

        UpgradeStatusJson upgradeStatusJson = gson.fromJson(resp.getBody().getBodyText(), UpgradeStatusJson.class);

        UpgradeGroupsJson[] upgradeGroupsJson = upgradeStatusJson.getUpgrade_groups();

        for (int i = 0; i < upgradeGroupsJson.length; i++) {
            resp = getAPIResponse(HTTPMethods.GET,upgradeGroupsJson[i].getHref());
            logger.logDebug("===========Upgrade Groups Info==========================");
            logger.logDebug(resp.getBody().getBodyText());

            resp = getAPIResponse(HTTPMethods.GET,upgradeGroupsJson[i].getHref());

            UpgradeGroupsJson upgradeGroupJson = gson.fromJson(resp.getBody().getBodyText(), UpgradeGroupsJson.class);

            UpgradeItemsJson[] upgradeItemsJson = upgradeGroupJson.getUpgrade_items();
            for (int j = 0; j < upgradeItemsJson.length; j++) {
                resp = getAPIResponse(HTTPMethods.GET,upgradeItemsJson[j].getHref());
                logger.logDebug("===========Upgrade Items Info==========================");
                logger.logDebug(resp.getBody().getBodyText());
                logger.logDebug("=====================================");
                UpgradeItemsJson upgradeItemJson = gson.fromJson(resp.getBody().getBodyText(), UpgradeItemsJson.class);


                for (int k=0;k < upgradeItemJson.getTasks().length ; k++) {
                    resp = getAPIResponse(HTTPMethods.GET, upgradeItemJson.getTasks()[k].getHref());
                    logger.logDebug("===========Upgrade Tasks Info==========================");
                    logger.logDebug(resp.getBody().getBodyText());
                    logger.logDebug("=====================================");
                }
            }
            logger.logDebug("=====================================");
        }
        logger.logDebug("==== Done with printing complete API output of Upgrade/Downgrade operation ====");
    }

    /**
     * Returns Upgrade Item corresponding to the Upgrade Status passed
     * @param type
     * @return
     * @throws Exception
     */
    private UpgradeItemsJson getUpgradeItem(UPGRADESTATUS type) throws Exception {
        UpgradeStatusJson upgradeStatusJson = getLastUpgradeStatus();
        UpgradeGroupsJson[] upgradeGroupsJson = upgradeStatusJson.getUpgrade_groups();

        if(upgradeGroupsJson.length > 0) {
            for (int i=0; i< upgradeGroupsJson.length ; i++) {

                HTTPResponse resp = getAPIResponse(HTTPMethods.GET,upgradeGroupsJson[i].getHref());
                UpgradeGroupsJson upgradeGroupJson = gson.fromJson(resp.getBody().getBodyText(), UpgradeGroupsJson.class);

                if(upgradeGroupJson.getUpgradeGroup().getStatus().contains(type.toString())) {
                    logger.logInfo("Status " + upgradeGroupJson.getUpgradeGroup().getStatus());
                    logger.logInfo("For Upgrade group " + upgradeGroupsJson[i].getHref());

                    UpgradeItemsJson[] upgradeItemsJson = upgradeGroupJson.getUpgrade_items();


                    for (int j=0; j< upgradeItemsJson.length ; j++) {
                        resp = getAPIResponse(HTTPMethods.GET, upgradeItemsJson[j].getHref());
                        UpgradeItemsJson upgradeItemJson = gson.fromJson(resp.getBody().getBodyText(), UpgradeItemsJson.class);

                        if(upgradeItemJson.getUpgradeItem().getStatus().contains(type.toString())) {
                            return upgradeItemJson;
                        }
                    }
                }
            }
        }
        else
            throw new Exception("Unable to retrieve Upgrade Status. The API output indicates that upgrade groups information was not gathered");

        return null;
    }

    /**
     * Gets last Upgrade Item which is in HOLDING state, for next manual intervention during EU
     * @return
     * @throws Exception
     */
    private UpgradeItemsJson getLastPendingUpgradeItem() throws Exception {
        UpgradeItemsJson upgradeItemsJson = getUpgradeItem(UPGRADESTATUS.HOLDING);

        logger.logInfo("HOLDING endpoint " + upgradeItemsJson.getHref());
        logger.logInfo("HOLDING endpoint " + upgradeItemsJson.getUpgradeItem().getText());

        return upgradeItemsJson;

    }

    /**
     * Wrapper method to handle manual intervention during EU
     * @throws Exception
     */
    public void proceedUpgradeAfterManualVerification() throws Exception {
        handleManualStep(getLastPendingUpgradeItem().getHref(), "COMPLETED");
        logger.logInfo("Proceeded after manual intervention");
    }

    /**
     * Handles manual intervention based on what user decides
     * @param uri
     * @param status - COMPLETED/PENDING
     */
    private void handleManualStep(String uri, String status) {
        Map<String, String> map = new HashMap<String, String>();
        map.put("{STATUS}", status);
        String requestBody = FileUtils.getJsonAsString("HandleManualVerificationDuringUpgrade.json", map);
        logger.logInfo("Request body  : " + requestBody);

        HTTPRequest req = new HTTPRequest(HTTPMethods.PUT, uri);
        req.setBody(new HTTPBody(requestBody));
        HTTPResponse resp = rc.sendHTTPRequest(req);

        System.out.println(resp.getStatusCode());  // validate the status code
    }


    // TODO - Move the above set of methods to another class; there is already a copy of this method in Cluster class.
    // Note - this method is currently required by getInstalledStackVersion() method
    public List<StackVersion> getStackVersions(){
        List<StackVersion> stackVersions = new ArrayList<StackVersion>();
        HTTPRequest req = new HTTPRequest(HTTPMethods.GET, clusterJson.getHref()+"/stack_versions");
        HTTPResponse resp = rc.sendHTTPRequest(req);

        StackVersionListJson stackVersionListJson = gson.fromJson(resp.getBody().getBodyText(), StackVersionListJson.class);

        int stackCount = stackVersionListJson.getItems().length;

        for(int i=0;i<stackCount;i++)
            stackVersions.add(new StackVersion(stackVersionListJson.getItems()[i].getHref()));

        return stackVersions;
    }

    public String getInstalledStackVersion() throws Exception {
        return getStackRepositoryVersion("INSTALLED");
    }

    public String getCurrentStackVersion() throws Exception {
        return getStackRepositoryVersion("CURRENT");
    }

    private String getStackRepositoryVersion(String version) throws Exception {
        List<StackVersion> stackVersions = getStackVersions();
        StackVersion desiredStackVersion=null;
        if(version.equalsIgnoreCase("CURRENT")) {
            for (StackVersion stackVersion : stackVersions) {
                if (stackVersion.getStackVersionDetailJson().getClusterStackVersions().getState().equalsIgnoreCase(version)) {
                    desiredStackVersion = stackVersion;
                    break;
                }
            }
        }
        else
            desiredStackVersion = stackVersions.get(stackVersions.size() - 1);  // Match the most recent installed version (status showed as UPGRADED if trying to fetch for before downgrade)

        if(desiredStackVersion == null)
            throw new Exception(String.format("Error while getting stack version information for the %s version", version));

        HTTPResponse resp = getAPIResponse(HTTPMethods.GET, desiredStackVersion.getStackVersionDetailJson().getRepository_versions()[0].getHref());
        RepositoryVersionJson repositoryVersionJson  = gson.fromJson(resp.getBody().getBodyText(), RepositoryVersionJson.class);

        return repositoryVersionJson.getRepositoryVersions().getRepository_version();
    }
    // TODO Move the above set of methods to another class

    //Failure related wrapper methods for EU
    public void injectSlaveFailure(String serviceName, String slaveComponentName) {
        getServiceComponent(serviceName, slaveComponentName).injectFailure();
    }

    public void fixSlaveFailure(String serviceName, String slaveComponentName) {
        getServiceComponent(serviceName, slaveComponentName).fixFailure();
    }

    public void injectServiceCheckFailure(String serviceName) {
        Service service = clusterJson.getService(serviceName);
        service.injectServiceCheckFailure();
    }

    public void fixServiceCheckFailure(String serviceName) {
        Service service = clusterJson.getService(serviceName);
        service.fixServiceCheckFailure();
    }

    public void injectMasterFailure() {
        // To be added
    }

    /**
     * Returns ServiceComponent object for the service and component name; move this method elsewhere later
     * @param serviceName
     * @param componentName
     * @return
     */
    public ServiceComponent getServiceComponent(String serviceName, String componentName) {
        Service service = clusterJson.getService(serviceName);
        List<ServiceComponent> serviceComponents = service.getSlaveComponents();
        ServiceComponent component = null;
        for(ServiceComponent serviceComponent : serviceComponents) {
            if(serviceComponent.getName().equalsIgnoreCase(componentName)) {
                component = serviceComponent;
                break;
            }
        }
        if(component == null)
            throw new RuntimeException("No component found for service " + serviceName);
        else
            return component;
    }

    /**
     * Retry failed step during EU; shoudl be generally called after fixing the error
     * @throws Exception
     */
    public void retryFailedStep() throws Exception {
        String uri = getLastFailedUpgradeItem().getHref();
        handleManualStep(uri, "PENDING");
    }

    /**
     * Paused upgrade, so that another action can be taken - like fix slave failure and restart service component such as DN
     * @throws Exception
     */
    public void pauseUpgrade() throws Exception {
        changeUpgradeState("ABORTED");
        logger.logInfo("Upgrade paused");

    }

    /**
     * Resumes upgrade; so it can continue from last paused state
     * @throws Exception
     */
    public void resumeUpgrade() throws Exception {
        changeUpgradeState("PENDING");
        logger.logInfo("Upgrade resumed");
    }

    /**
     * Private method to change upgrade status
     * @param status
     * @throws Exception
     */
    private void changeUpgradeState(String status) throws Exception {
        Map<String,String> map = new HashMap<String,String>();
        map.put("{STATUS}", status);
        String body = FileUtils.getJsonAsString("UpgradeStateChangeRequest.json",map);
        HTTPRequest req = new HTTPRequest(HTTPMethods.PUT, clusterJson.getHref()+"/upgrades/" + getLastUpgradeRequestID());
        req.setBody(new HTTPBody(body));
        HTTPResponse resp = rc.sendHTTPRequest(req);
        logger.logInfo(resp.getBody().getBodyText());
    }

}
