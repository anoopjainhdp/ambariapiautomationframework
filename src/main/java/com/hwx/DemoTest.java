package com.hwx;

import java.util.Date;

import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import com.hwx.ambariapilib.AmbariManager;
import com.hwx.ambariapilib.host.Host;
import com.hwx.utils.logging.AmbariLogger;

/**
 * Created by ajain on 9/11/15.
 */
public class DemoTest {

	AmbariLogger logger = AmbariLogger.getAmbariLoggerInstance(DemoTest.class.getSimpleName());

    AmbariManager ambariManager;

    @BeforeTest
    public void init(){
        ambariManager = new AmbariManager();
    }

    @Test
    public void testHostState(){
        Host[] hosts = ambariManager.getClusters()[0].getHosts();

        for(int i=0;i<hosts.length;i++){
            logger.logInfo("Host : "+hosts[i]+" State : "+hosts[i].getHealthState());
            Assert.assertEquals(hosts[i].getHealthState(), "HEALTHY");
        }
    }

    @Test
    public void testStartHDFSService(){
        long startTime = (new Date()).getTime();
        logger.logInfo("Start time : " +startTime);
        ambariManager.getClusters()[0].startService("HDFS");
        long endTime = (new Date()).getTime();
        logger.logInfo("End time : " +endTime);
        logger.logInfo("Time to start the service (in minutes): "+((endTime-startTime)/(1000*60)));

    }
 
}
