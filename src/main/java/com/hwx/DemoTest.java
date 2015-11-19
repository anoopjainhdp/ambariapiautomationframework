package com.hwx;

import com.hwx.ambariapilib.AmbariManager;
import com.hwx.ambariapilib.host.Host;
import com.hwx.ambariapilib.json.upgrade.UpgradeStatusJson;
import com.hwx.ambariapilib.service.ServiceAlerts;
import com.hwx.ambariapilib.upgrade.ExpressUpgrade;
import com.hwx.ambariapilib.upgrade.StackUpgrade;
import com.hwx.utils.logging.LogManager;
import java.util.ArrayList;
import java.util.Date;

import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.hwx.ambariapilib.AmbariManager;
import com.hwx.ambariapilib.host.Host;
import com.hwx.utils.LinuxCommandExecutor;
import com.hwx.utils.LinuxCommandExecutor.IGNORE_ERRORS;
import com.hwx.utils.ProcessData.Types_Of_Data;
import com.hwx.utils.logging.LogManager;

/**
 * Created by ajain on 9/11/15.
 */
public class DemoTest {

    LogManager logger = LogManager.getLoggerInstance(DemoTest.class.getSimpleName());

    AmbariManager ambariManager;

    @BeforeTest
    public void init(){
        ambariManager = new AmbariManager();
    }

    @Test
    public void testHostState(){
        ArrayList<Host> hosts = ambariManager.getClusters().get(0).getHosts();

        for(int i=0;i<hosts.size();i++){
            logger.logInfo("Host : "+hosts.get(i)+" State : "+hosts.get(i).getHealthState());
            Assert.assertEquals(hosts.get(i).getHealthState(), "HEALTHY");
        }
    }

    @Test
    public void testStartHDFSService(){
        long startTime = (new Date()).getTime();
        logger.logInfo("Start time : " +startTime);
        ambariManager.getClusters().get(0).startService("HDFS");
        long endTime = (new Date()).getTime();
        logger.logInfo("End time : " +endTime);
        logger.logInfo("Time to start the service (in minutes): "+((endTime-startTime)/(1000*60)));

    }

    @Test
    public void testStopHDFSService(){
        long startTime = (new Date()).getTime();
        logger.logInfo("Start time : " +startTime);
        ambariManager.getClusters().get(0).stopService("HDFS");
        long endTime = (new Date()).getTime();
        logger.logInfo("End time : " +endTime);
        logger.logInfo("Time to stop the service (in minutes): "+((endTime-startTime)/(1000*60)));

    }

    @Test
    public void testAddHost(){
        ambariManager.getClusters().get(0).createHost("c6404.ambari.apache.org");
    }

    @Test
    public void testService(){
        logger.logInfo(ambariManager.getClusters().get(0).getServices().get(1).getClusterServiceDetailsJson().getServiceInfo().getService_name());
    }

    @Test
    public void testCreateService(){
        ambariManager.getClusters().get(0).createServices("HIVE");
    }

    @Test
    public void testStartService(){
        ambariManager.getClusters().get(0).startService("HDFS");
    }

    @Test
    public void testStopService(){
        ambariManager.getClusters().get(0).stopService("HDFS");
    }

    @Test
    public void testStartAllServices(){
        ambariManager.getClusters().get(0).startAllServices();
    }

    @Test
    public void testStopAllServices(){
        ambariManager.getClusters().get(0).stopAllServices();
    }


    @Test
    public void testHosts(){
        logger.logInfo(ambariManager.getClusters().get(0).getHosts().get(0).getHostJson().getHosts().getHost_name());
    }



    @Test
    public void testViews(){

        for(int i=0;i<ambariManager.getClusters().get(0).getViews().size();i++)
            System.out.println(ambariManager.getClusters().get(0).getViews().get(i).getViewJson().getViewInfo().getView_name());
    }
    @Test
    public void testViewsVersions(){

        for(int i=0;i<ambariManager.getClusters().get(0).getViews().get(0).getViewVersions().size();i++)
            System.out.println(ambariManager.getClusters().get(0).getViews().get(0).getViewVersions().get(i).getViewVersionJson().getViewVersionInfo().getVersion());
    }

    @Test
    public void testViewInstances(){

        for(int i=0;i<ambariManager.getClusters().get(0).getViews().get(0).getViewVersions().get(0).getViewInstances().size();i++)
            System.out.println(ambariManager.getClusters().get(0).getViews().get(0).getViewVersions().get(0).getViewInstances().get(i).getViewLongInstanceJson().getViewInstanceInfo().getInstance_name());
    }

    @Test
    public void testCreateViewInstances(){
        ambariManager.getClusters().get(0).getViews().get(0).getViewVersions().get(0).createInstance("NewInstance");
    }

    @Test
    public void testCreateCluster(){
        ambariManager.createCluster("test");
    }

    @Test
    public void testServiceComponents(){
        ambariManager.getClusters().get(0).getServices().get(0).getServiceComponents();
    }

    @Test
    public void testCreateComponent(){
        ambariManager.getClusters().get(0).getServices().get(0).createComponent("TestComponent");
    }

    @Test
    public void testCommandLocalHost(){
        LinuxCommandExecutor lex = new LinuxCommandExecutor("", "","" ,new String[] {"ls -l ~/Documents/ | grep ambari; cd ~/Downloads ;pwd; ls"});
        String value = lex.executeCommandLocalHost(Types_Of_Data.OUTPUT, IGNORE_ERRORS.FALSE);
        int exitCode = lex.getExitCode();
        logger.logInfo(value);
        logger.logInfo("Exit code: " +exitCode);
    }

    @Test
    public void testCommandRemotely(){
        LinuxCommandExecutor lex = new LinuxCommandExecutor("172.22.101.164", "root",new String[] {"ambari-server status"});
        String value = lex.executeCommandSequenceRemotely(Types_Of_Data.OUTPUT, IGNORE_ERRORS.FALSE);
        int exitCode = lex.getExitCode();
        logger.logInfo(value);
        logger.logInfo("Exit code: " +exitCode);
    }

    @Test
    public void testAlerts() {
        ServiceAlerts obj = new ServiceAlerts("http://172.22.101.164:8080/api/v1/clusters/cl1/services/HDFS");
        System.out.println("COUNT OF OK ALERTS " + obj.getOkAlertsCount());
        System.out.println("COUNT OF WARNINGS ALERTS " + obj.getWarningAlerts());
        System.out.println("COUNT OF UNKNOWN ALERTS " + obj.getUnknownAlertsCount());
        System.out.println("COUNT OF CRITICAL ALERTS " + obj.getCriticalAlertsCount());
        System.out.println("CRITICAL ALERT HREF" + obj.getCriticalAlerts());
        System.out.println("Array of OK ALERT HREF" + obj.getOkAlerts());
        System.out.println("Array of CRITICAL ALERT HREF" + obj.getCriticalAlerts());
        System.out.println("Array of UNKNOWN ALERT HREF" + obj.getUnknownAlerts());
        System.out.println("Array of WARNINGS ALERT HREF" + obj.getWarningAlerts());
    }
}

