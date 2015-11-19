package com.hwx.utils;

import com.hwx.ambariapilib.AmbariItems;
import com.hwx.ambariapilib.common.Request;
import com.hwx.utils.logging.LogManager;

/**
 * Created by ajain on 11/5/15.
 */
public class WaitUtil {

    public static final int timeOutInSec = 20000; //Integer.parseInt(ConfigProperties.TIMEOUT.getKey());
    static LogManager logger = LogManager.getLoggerInstance(AmbariItems.class.getSimpleName());

    //Wait for request to be completed
    public static void waitForRequestToBeCompleted(String clusterAPIUrl, int requestId) throws Exception {
        Request req = new Request(clusterAPIUrl+"/requests/"+requestId);

        int iteration = 1;
        int waitTime = 0;
        String requestStatus = req.getRequestDetailJson().getRequests().getRequest_status();

        while((waitTime < timeOutInSec) && (!requestStatus.equals("COMPLETED"))){
            //Increase wait time linearly
            waitForIncreasingInterval(WaitTime.LONGWAIT,iteration);
            waitTime += WaitTime.LONGWAIT.getTime() * iteration;

            logger.logInfo("Wait for " + (WaitTime.LONGWAIT.getTime() * iteration) + " seconds. Total Wait Time : " + waitTime + " seconds");

            //Send the request again
            req = new Request(clusterAPIUrl+"/requests/"+requestId);
            requestStatus = req.getRequestDetailJson().getRequests().getRequest_status();
            if(requestStatus.equalsIgnoreCase("FAILED"))
                throw new Exception("Error while processing the request: " + req.getRequestDetailJson().getRequests().getRequest_context());
//            iteration *= 2;
        }

        if(waitTime >= timeOutInSec ){
            //Throw Timeout Exception
            //ToDo Define timeout exception
        }

        logger.logInfo("Request completed successfully");
    }

    //Wait for defined period
    public static void waitForFixedInterval(WaitTime waitTime){
        waitForFixedInterval(waitTime.getTime());
    }


    //Wait for fixed interval
    public static void waitForFixedInterval(int intervalInSec){
        try{
            //ToDo how to log this??
//            LogManager.getLoggerInstance().logInfo("Waiting for "+intervalInSec+" Seconds");
            Thread.sleep(intervalInSec*1000);
        }
        catch (Exception e){
//            LogManager.getLoggerInstance().logWarning("Issue while waiting : "+e.getMessage());
        }
    }


    //This function can be used where wait time need to be increased with iteration
    public static void waitForIncreasingInterval(WaitTime intervalInSec, int increase){
        waitForIncreasingInterval(intervalInSec.getTime(), increase);
    }

    //This function can be used where wait time need to be increased with iteration
    public static void waitForIncreasingInterval(int intervalInSec, int increase){
        waitForFixedInterval(intervalInSec*increase);
    }


}
