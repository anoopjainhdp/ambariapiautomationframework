package com.hwx.ambariapilib.json.host;

import com.hwx.ambariapilib.json.service.ServiceComponentShortInfoJson;

/**
 * Created by ajain on 10/5/15.
 */
public class ComponentsJson {
    private String href;
    private ServiceComponentShortInfoJson ServiceComponentInfo;

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public ServiceComponentShortInfoJson getServiceComponentInfo() {
        return ServiceComponentInfo;
    }

    public void setServiceComponentInfo(ServiceComponentShortInfoJson serviceComponentInfo) {
        ServiceComponentInfo = serviceComponentInfo;
    }
}
