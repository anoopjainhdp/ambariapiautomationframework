package com.hwx.ambariapilib.json.service;

/**
 * Created by ajain on 9/29/15.
 */
public class ServiceRequestJson {
    private String href;
    private ServiceRequest Requests;

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public ServiceRequest getRequests() {
        return Requests;
    }

    public void setRequests(ServiceRequest requests) {
        Requests = requests;
    }
}
