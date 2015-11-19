

package expressupgrade;

import com.hwx.ambariapilib.common.IDConstants;
import com.hwx.ambariapilib.json.upgrade.TasksJson;
import com.hwx.ambariapilib.service.Service;
import com.hwx.ambariapilib.service.ServiceComponent;
import com.hwx.ambariapilib.upgrade.StackUpgrade;
import com.hwx.ambariapilib.upgrade.UpgradeParams;
import com.hwx.utils.WaitUtil;
import com.hwx.utils.config.ConfigProperties;
import com.hwx.utils.validation.ValidationUtils;
import common.TestBase;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by vsharma on 11/8/15.
 */
public class TestExpressUpgrade extends TestBaseUpgrade {

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

    /**
     * Tests Express Upgrade from main to maint; major to major; main to major release depending on input passed
     * Validations:
     * Services are up, upgrade API status - COMPLETED, Database entries - show latest version
     * Components are up
     */
    @Test
    public void testBasicUpgrade() throws Exception {
        try {
            registerVersionAndInstallPackages();
            stackUpgrade.submitExpressUpgrade(upgradeParams);

            stackUpgrade.waitforOperationCompletion(true);
            postUpgradeValidations();
        }
        catch(Exception e) {
            e.printStackTrace();
            throw e;
        }
        finally {
            printUpgradeOutput();
        }
    }

    /**
     * Tests Downgrade from main to maint; major to major; main to major release depending on input passed
     * This would trigger downgrade just before finalize operation
     */
    @Test   //(dependsOnMethods = { "testBasicUpgrade" })
    public void testBasicDowngrade() throws Exception {
        try {
            checkDowngradeSupportForStack();

            registerVersionAndInstallPackages();
            stackUpgrade.submitExpressUpgradeTillFinalize(upgradeParams);

            printUpgradeOutput();

            postUpgradeValidations();
            stackUpgrade.submitDowngradeAfterExpressUpgrade();

            stackUpgrade.waitforOperationCompletion(false);
            postUpgradeValidations();
        }
        catch(Exception e) {
            e.printStackTrace();
            throw e;
        }
        finally {
            printUpgradeOutput();
        }
    }

    /**
     * Tests Express Upgrade from maint to maint to major like 2.2.8 to 2.2.9 to 2.3.0
     */
    @Test
    public void testMultilevelUpgrade() throws Exception {

        try {
            registerVersionAndInstallPackages();
            stackUpgrade.submitExpressUpgrade(upgradeParams);

            stackUpgrade.waitforOperationCompletion(true);

            logger.logInfo("First level EU complete");
            WaitUtil.waitForFixedInterval(20);
        }
        catch(Exception e) {
            e.printStackTrace();
            throw e;
        }
        finally {
            printUpgradeOutput();
        }

        try {
            setEUBuildParams(conf.getString(ConfigProperties.STACKNAME.getKey()), conf.getString(ConfigProperties.STACKVERSION_FOR_SECOND_LEVEL_UPGRADE.getKey()), conf.getString(ConfigProperties.BUILDNUMBER_FOR_SECOND_LEVEL_UPGRADE.getKey()),
                    conf.getString(ConfigProperties.OPERATING_SYSTEM.getKey()), conf.getString(ConfigProperties.HDP_BASEURL_FOR_SECOND_LEVEL_UPGRADE.getKey()), conf.getString(ConfigProperties.HDPUTILS_BASEURL_FOR_SECOND_LEVEL_UPGRADE.getKey()));
            registerVersionAndInstallPackages();
            stackUpgrade.submitExpressUpgrade(upgradeParams);

            stackUpgrade.waitforOperationCompletion(true);

            logger.logInfo("Second level EU complete");
            WaitUtil.waitForFixedInterval(20);
        }
        catch(Exception e) {
            e.printStackTrace();
            throw e;
        }
        finally {
            printUpgradeOutput();
        }
    }

    /**
     * Tests Express Upgrade after Ambari is upgraded. Specifically required for HDP 2.1 to 2.3 path
     */
    @Test(enabled = false)
    public void testUpgradeAfterAmbariUpgrade() throws Exception {

        try {
            // TODO - Add checks and code for Ambari upgrade
            registerVersionAndInstallPackages();
            stackUpgrade.submitExpressUpgrade(upgradeParams);

            stackUpgrade.waitforOperationCompletion(true);

            logger.logInfo("EU complete");
        }
        catch(Exception e) {
            e.printStackTrace();
            throw e;
        }
        finally {
            printUpgradeOutput();
        }
    }

    @Test
    public void sampleTC() throws Exception {
        System.out.println(stackUpgrade.getInstalledStackVersion());
        System.out.println(stackUpgrade.getCurrentStackVersion());
    }

    @Test
    public void sampleTest() throws Exception {
        // stackUpgrade.testrs();
        stackUpgrade.retryFailedStep();
        List<TasksJson> failedTasks = stackUpgrade.getAllFailedTasks();
        stackUpgrade.proceedUpgradeAfterManualVerification();
        System.out.println(stackUpgrade.getInstalledStackVersion());
        System.out.println(stackUpgrade.getCurrentStackVersion());

        stackUpgrade.injectSlaveFailure("HDFS", "DataNode");
        stackUpgrade.fixSlaveFailure("HDFS", "DataNode");
        stackUpgrade.injectServiceCheckFailure("HDFS");
        stackUpgrade.fixServiceCheckFailure("HDFS");
    }



}
