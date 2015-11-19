package expressupgrade;

import com.hwx.ambariapilib.common.IDConstants;
import com.hwx.ambariapilib.json.upgrade.TasksJson;
import com.hwx.ambariapilib.service.ServiceComponent;
import com.hwx.utils.WaitUtil;
import com.hwx.utils.config.ConfigProperties;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Created by vsharma on 11/18/15.
 */
public class TestExpressUpgradeST extends TestBaseUpgrade {

    @BeforeClass
    public void initialize() throws Exception {
        if(stackUpgrade == null)
            stackUpgrade = ambariManager.getClusters().get(0).initializeStackUpgrade("express");
    }

    @BeforeMethod
    public void initializeEUParams() {
        try {
            setEUBuildParams(conf.getString(ConfigProperties.STACKNAME.getKey()), conf.getString(ConfigProperties.STACKVERSION_TO_UPGRADE.getKey()), conf.getString(ConfigProperties.BUILDNUMBER_TO_UPGRADE.getKey()),
                    conf.getString(ConfigProperties.OPERATING_SYSTEM.getKey()), conf.getString(ConfigProperties.HDP_BASEURL.getKey()), conf.getString(ConfigProperties.HDPUTILS_BASEURL.getKey()));
            setEUOperationParams("false", "false", "true", "false", "false");       // Common to all tests, except very few where skip failures needs to be tested. Those tests will explicitly call this method at their start
        } catch (Exception e) {
            logger.logError(e.getStackTrace().toString());
        }
    }

    @Test
    public void dryRunTC1() {
        String clusterName= ambariManager.getClusters().get(0).getClusterJson().getClusters().getCluster_name();
        System.out.println(clusterName);
    }

    @Test
    public void dryRunTC2() throws Exception {
        String clusterName= ambariManager.getClusters().get(0).getClusterJson().getClusters().getCluster_name();
        System.out.println("Cluster name:: " + clusterName);
    }

    @Test
    public void testEUwithSkipSlaveFailures() throws Exception {
        try {
            setEUOperationParams("false", "true", "true", "false", "false");

            String slaveFailureService = "HDFS";
            String slaveFailureComponent = "DATANODE";
            String serviceCheckFailureService = "YARN";
            String failedTaskCommand = "RESTART HDFS/DATANODE";
            String skipFailureMessage = "Verifying Skipped Failures";


            registerVersionAndInstallPackages();

            stackUpgrade.injectSlaveFailure(slaveFailureService, slaveFailureComponent);
            //stackUpgrade.injectServiceCheckFailure(serviceCheckFailureService);

            // Get slave related information
            ServiceComponent slaveComponent = stackUpgrade.getServiceComponent(slaveFailureService, slaveFailureComponent);
            int totalSlaves = slaveComponent.getServiceComponentDetailJson().getServiceComponentInfo().getTotal_count();

            stackUpgrade.submitExpressUpgradeTillSpecificManualStep(upgradeParams, skipFailureMessage);

            // Let the EU pause after "Verify Skipped Failure" stage for slave component

            // Validations start when EU reaches skipped failures phase for slave component
            List<TasksJson> failedTasks = stackUpgrade.getAllFailedTasks();

            Assert.assertEquals(failedTasks.size(), totalSlaves, "Error while checking the number of failed slave count");

            for (TasksJson task : failedTasks) {
                Assert.assertTrue(task.getTask().getCommand_detail().equalsIgnoreCase(failedTaskCommand), "Error while checking the failed slave information");
                Assert.assertTrue(task.getTask().getStderr().contains(IDConstants.TEST_SERVICE_COMPONENT_INJECTED_FAILURE), "Error while checking the failed slave information");
            }

            // Validations end when EU reaches skipped failures phase for slaves. Continue the EU
            stackUpgrade.proceedUpgradeAfterManualVerification();

            // Verification complete for slave failure, now fix the errors
            stackUpgrade.fixSlaveFailure(slaveFailureService, slaveFailureComponent);

            // Wait for EU to successfully complete
            try {
                stackUpgrade.waitforOperationCompletion(true);
            } catch (Exception e) {
                logger.logInfo("Upgrade failed as expected");
                stackUpgrade.pauseUpgrade();
                stackUpgrade.getServiceComponent(slaveFailureService, slaveFailureComponent).restart();
                stackUpgrade.resumeUpgrade();
            }
            stackUpgrade.waitforOperationCompletion(true);
            // pause upgrade

            // resume upgrade

            postUpgradeValidations();
        }
        finally {
            printUpgradeOutput();
        }
    }

    @Test
    public void testEUwithSkipServiceCheckFailures() throws Exception {
        try {
            setEUOperationParams("true", "false", "true", "false", "false");

            String serviceCheckFailureService = "YARN";
            String failedTaskCommand = "SERVICE_CHECK YARN";
            String skipFailureMessage = "Verifying Skipped Failures";

            registerVersionAndInstallPackages();

            //stackUpgrade.injectSlaveFailure(slaveFailureService, slaveFailureComponent);
            stackUpgrade.injectServiceCheckFailure(serviceCheckFailureService);

            // Get slave related information
            //ServiceComponent slaveComponent = stackUpgrade.getSlaveComponent(slaveFailureService, slaveFailureComponent);
            //int totalSlaves = slaveComponent.getServiceComponentDetailJson().getServiceComponentInfo().getTotal_count();

            stackUpgrade.submitExpressUpgradeTillSpecificManualStep(upgradeParams, skipFailureMessage);

            // Let the EU pause after "Verify Skipped Failure" stage for slave component

            // Validations start when EU reaches skipped failures phase for slave component
            List<TasksJson> failedTasks = stackUpgrade.getAllFailedTasks();

            Assert.assertEquals(failedTasks.size(), 1, "Error while checking the number of failed tasks due to service check failures");

            for (TasksJson task : failedTasks) {
                Assert.assertTrue(task.getTask().getCommand_detail().equalsIgnoreCase(failedTaskCommand), "Error while checking the failed service check information");
                Assert.assertTrue(task.getTask().getStderr().contains(IDConstants.TEST_SERVICE_CHECK_INJECTED_FAILURE), "Error while checking the failed service check information");
            }
            // Validations end when EU reaches skipped failures phase for slaves
            // Continue the EU
            stackUpgrade.proceedUpgradeAfterManualVerification();

            // Verification complete for slave failure, now fix the errors
            stackUpgrade.fixServiceCheckFailure(serviceCheckFailureService);

            // Wait for EU to successfully complete
            stackUpgrade.waitforOperationCompletion(true);

            postUpgradeValidations();

            // Now let's cover the test to Verify cluster component/service state after a successful upgrade/downgrade for a period of n hours
            logger.logInfo("Running additional test to verify cluster component/service state after a successful upgrade for a period of n hours");
            WaitUtil.waitForFixedInterval(60 * 30);   // Wait 30 minutes
            postUpgradeValidations();
        }
        finally {
            printUpgradeOutput();
        }
    }

    @Test
    public void testDowngradeWhenFailureOccursBeforeFinalize() throws Exception {
        try {
            checkDowngradeSupportForStack();
            setEUOperationParams("true", "true", "true", "false", "false");

            String serviceCheckFailureService = "HDFS";
            String failedTaskCommand = "SERVICE_CHECK HDFS";
            String skipFailureMessage = "Verifying Skipped Failures";

            //registerVersionAndInstallPackages();

            stackUpgrade.injectServiceCheckFailure(serviceCheckFailureService);
            stackUpgrade.submitExpressUpgradeTillSpecificManualStep(upgradeParams, skipFailureMessage);

            List<TasksJson> failedTasks = stackUpgrade.getAllFailedTasks();
            Assert.assertEquals(failedTasks.size(), 1, "Error while checking the number of failed tasks due to service check failures");
            Assert.assertTrue(failedTasks.get(0).getTask().getCommand_detail().equalsIgnoreCase(failedTaskCommand), "Error while checking the failed service check information");

            stackUpgrade.submitDowngradeAfterExpressUpgrade();

            stackUpgrade.waitforOperationCompletion(false);
            postUpgradeValidations();
        }
        finally {
            printUpgradeOutput();
        }
    }


}
