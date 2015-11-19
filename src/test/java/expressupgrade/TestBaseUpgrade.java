package expressupgrade;

import com.hwx.ambariapilib.AmbariManager;
import com.hwx.ambariapilib.common.IDConstants;
import com.hwx.ambariapilib.host.Host;
import com.hwx.ambariapilib.host.HostComponent;
import com.hwx.ambariapilib.service.Service;
import com.hwx.ambariapilib.upgrade.StackUpgrade;
import com.hwx.ambariapilib.upgrade.UpgradeParams;
import com.hwx.utils.LinuxCommandExecutor;
import com.hwx.utils.WaitUtil;
import com.hwx.utils.validation.DBValidationUtils;
import com.hwx.utils.validation.ValidationUtils;
import common.TestBase;
import org.testng.Assert;
import org.testng.SkipException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by vsharma on 11/18/15.
 */
public class TestBaseUpgrade extends TestBase {

    protected StackUpgrade stackUpgrade;
    protected UpgradeParams upgradeParams = new UpgradeParams();
    String clusterName;
    private List<String> clusterHosts = new ArrayList<String>();
    String currentVersion;

    private String getCurrentVersion() throws Exception {
        if(this.currentVersion == null)
            setCurrentVersion();

        return this.currentVersion;
    }

    private void setCurrentVersion() throws Exception {
            this.currentVersion = stackUpgrade.getCurrentStackVersion();
    }

    private void populateHosts() {
        ArrayList<Host> hosts = ambariManager.getClusters().get(0).getHosts();

        for(Host host : hosts)
            clusterHosts.add(host.getName());
    }

    private List<String> getClusterHosts() {
        if (clusterHosts.size() == 0)
            populateHosts();

        return clusterHosts;
    }

    public String getClusterName() {
        if(clusterName == null)
            this.clusterName = ambariManager.getClusters().get(0).getClusterJson().getClusters().getCluster_name();;

        return this.clusterName;
    }

    protected void postUpgradeValidations() throws Exception {
        String SERVICE_CHECK = "Service Checks";
        String HOST_COMPONENT_CHECK = "Host Component Checks";
        String DB_CHECK = "Database Checks";
        String HDP_SELECT_CHECK = "HDP Command Checks";
        String SYM_LINK_CHECK = "Symlink Checks";
        String ALERTS_CHECK = "Alerts Checks";

        Map<String,Boolean> validations= new HashMap<String, Boolean>();

        validations.put(SERVICE_CHECK, false);
        validations.put(HOST_COMPONENT_CHECK, false);

        if(ValidationUtils.isAllServiceStarted())
            validations.put(SERVICE_CHECK, true);
        else {
            logger.logInfo("List of failed service(s)");
            ArrayList<Service> failedServices = ValidationUtils.getFailedServices();
            for (Service service : failedServices)
                logger.logInfo(service.getName());

        }

        if(ValidationUtils.isAllHostComponentStarted())
            validations.put(HOST_COMPONENT_CHECK,true);
        else {
            logger.logInfo("List of failed component(s)");
            ArrayList<HostComponent> failedComponentns = ValidationUtils.getFailedComponents();
            for (HostComponent component : failedComponentns)
                logger.logInfo(component.getName());
        }

        boolean isValidationFailed = false;
        for(String validation : validations.keySet()) {
            boolean status = validations.get(validation);
            logger.logInfo(validation + " : " + status);

            if(!status)
                isValidationFailed = true;
        }

        if(isValidationFailed) {
            logger.logError("Observed one or more errors as part of post upgrade validations - see output above");
            throw new Exception("Observed one or more errors as part of post upgrade validations");
        }

        verifyHDPSelectStatus();
        databaseValidations();

    }

    protected void verifyHDPSelectStatus() throws Exception {
        String cmdOutput= null;
        String user = "root";

        for(String host : getClusterHosts()) {
            try {
                cmdOutput = LinuxCommandExecutor.executeCommand(host, user, new String[]{"hdp-select status | grep -v " + getCurrentVersion() + " | grep -v None"}[0]);
            } catch (Exception e) {
                if (cmdOutput == null)
                    logger.logInfo("hdp-select status command returned zero output as expected on host " + host);
                else
                    throw new Exception("Error in output of hdp-select Got output as " + cmdOutput);
            }
        }
    }

    protected void databaseValidations() throws Exception {
        logger.logInfo("Performing validation in database tables");

        Assert.assertEquals(DBValidationUtils.verifyCurrentClusterVersion(getClusterName(), getCurrentVersion()), true, "Error while verifying cluster current version");
        Assert.assertEquals(DBValidationUtils.verifyHostsVersion(getClusterHosts(), getCurrentVersion()), true, "Error while verifying host's current version");
        Assert.assertEquals(DBValidationUtils.verifyStackRegisteredRepo(getCurrentVersion()), true, "Error while verifying stack repo");

        for(String host : getClusterHosts())
            Assert.assertEquals(DBValidationUtils.verifyHostComponentsState(host, getCurrentVersion(), clusterName), true, "Error while verifying host component state");

        logger.logInfo("Finished performing validation in database tables");
    }

    protected void registerVersionAndInstallPackages() throws Exception {
        stackUpgrade.registerNewVersion(upgradeParams.getStackName() + "-" + upgradeParams.getStackVersion() + "." + upgradeParams.getBuildNumber(),
                upgradeParams.getStackVersion(), upgradeParams.getBuildNumber(), upgradeParams.getOperatingSystem(), upgradeParams.getHdpBaseUrl(), upgradeParams.getHdpUtilsBaseUrl());

        boolean installSuccess = false;

        for (int i=1; i<= IDConstants.INSTALL_PACKAGE_RETRY_COUNT ; i++) {

            try {
                stackUpgrade.submitInstallPackageRequest(upgradeParams.getStackName(), upgradeParams.getStackVersion(), upgradeParams.getBuildNumber());
                WaitUtil.waitForFixedInterval(10);
                installSuccess = true;
            } catch (Exception e) {
                e.printStackTrace();
                logger.logInfo("Retrying Install package " + i + " time");
            }
            if (installSuccess) {
                logger.logInfo("Package Installation successful on all hosts");
                return;
            }
        }
        if(!installSuccess)
            throw new Exception("Error during package installation. Test execution will abort");

    }

    /**
     * Prints output of Upgrade/Downgrade operation
     */
    protected void printUpgradeOutput() throws Exception {
        stackUpgrade.printEntireUpgradeOutput();
    }

    protected void setEUOperationParams(String skipServiceCheckFailure, String skipSlaveFailures, String skipPrerequisiteChecks, String failOnCheckWarnings, String skipManualVerification) {
        upgradeParams.setSkipServiceCheckFailure(skipServiceCheckFailure);
        upgradeParams.setSkipSlaveFailures(skipSlaveFailures);
        upgradeParams.setSkipPrerequisiteChecks(skipPrerequisiteChecks);
        upgradeParams.setFailOnCheckWarnings(failOnCheckWarnings);
        upgradeParams.setSkipManualVerification(skipManualVerification);
    }

    protected void setEUBuildParams(String stackName, String stackVersion, String buildNumber, String os, String hdpurl, String hdpUtilsurl) {
        upgradeParams.setStackName(stackName);
        upgradeParams.setStackVersion(stackVersion);
        upgradeParams.setBuildNumber(buildNumber);
        upgradeParams.setOperatingSystem(os);
        upgradeParams.setHdpBaseUrl(hdpurl);
        upgradeParams.setHdpUtilsBaseUrl(hdpUtilsurl);
    }

    private boolean isDowngradeSupported() throws Exception {
        String VERSION_TO_CHECK = "2.1";
        String stackVersion = stackUpgrade.getCurrentStackVersion();

        if(stackVersion.startsWith(VERSION_TO_CHECK)) {
            logger.logInfo(String.format("Downgrade is not supported for the chosen stack with version %s", stackVersion));
            return false;
        }
        else
            return true;
    }

    protected void checkDowngradeSupportForStack() throws Exception {
        if(!isDowngradeSupported()) {
            throw new SkipException("Skipping this test as downgrade is unsupported");
        }
    }



}
