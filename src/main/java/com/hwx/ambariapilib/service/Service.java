package com.hwx.ambariapilib.service;

import com.hwx.ambariapilib.AmbariItems;
import com.hwx.ambariapilib.common.IDConstants;
import com.hwx.ambariapilib.json.cluster.ClusterServiceDetailsJson;
import com.hwx.ambariapilib.json.service.ServiceComponentJson;
import com.hwx.clientlib.http.HTTPMethods;
import com.hwx.clientlib.http.HTTPRequest;
import com.hwx.clientlib.http.HTTPResponse;
import com.hwx.utils.LinuxCommandExecutor;
import com.hwx.utils.ProcessData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by ajain on 10/5/15.
 */
public class Service extends AmbariItems {

    private ClusterServiceDetailsJson clusterServiceDetailsJson;

    public Service(String serviceAPIUrl){
        HTTPRequest req = new HTTPRequest(HTTPMethods.GET, serviceAPIUrl);
        HTTPResponse resp = rc.sendHTTPRequest(req);

        clusterServiceDetailsJson = gson.fromJson(resp.getBody().getBodyText(), ClusterServiceDetailsJson.class);
    }

    public ClusterServiceDetailsJson getClusterServiceDetailsJson() {
        return clusterServiceDetailsJson;
    }

    public void setClusterServiceDetailsJson(ClusterServiceDetailsJson clusterServiceDetailsJson) {
        this.clusterServiceDetailsJson = clusterServiceDetailsJson;
    }

    public List<ServiceComponent> getServiceComponents(){
        List<ServiceComponent> serviceComponents = new ArrayList<ServiceComponent>();

        HTTPRequest req = new HTTPRequest(HTTPMethods.GET, clusterServiceDetailsJson.getHref()+"/components");
        HTTPResponse resp = rc.sendHTTPRequest(req);

        ServiceComponentJson serviceComponentsJson = gson.fromJson(resp.getBody().getBodyText(), ServiceComponentJson.class);

        int numComponents = serviceComponentsJson.getGetServiceComponents().length;

        for(int i=0;i<numComponents;i++)
            serviceComponents.add(new ServiceComponent(serviceComponentsJson.getGetServiceComponents()[i].getHref()));

        return serviceComponents;
    }

    //Create a component in the service
    public void createComponent(String components){
        HTTPRequest req = new HTTPRequest(HTTPMethods.POST, clusterServiceDetailsJson.getHref()+"/components/"+components);
        HTTPResponse resp = rc.sendHTTPRequest(req);

        logger.logInfo(resp.getBody().getBodyText());
    }

    //Get service status
    public String getState(){
        return clusterServiceDetailsJson.getServiceInfo().getState();
    }

    //Get service name
    public String getName(){
        return clusterServiceDetailsJson.getServiceInfo().getService_name();
    }

    //Is Service started
    public boolean isStarted(){
        return getState().equals("STARTED");
    }

    /**
     * Returns list of all hosts where one or more components of the service are installed
     * @return
     */
    public Set<String> getHosts() {
        List<ServiceComponent> serviceComponents = getServiceComponents();
        Set<String> hosts = new HashSet<String>();

        for(ServiceComponent serviceComponent : serviceComponents) {
            for(String host : serviceComponent.getHosts())
                hosts.add(host);
        }
        return hosts;
    }

    public void injectServiceCheckFailure() {
        logger.logInfo("Injecting failure for Service check on all hosts");
        for(String host : getHosts()) {
            LinuxCommandExecutor.executeCommand(host, "root", String.format("for file in `find /var/lib/ambari-agent/ -path */common-services/%s/*/service_check.py`;" +
                    " do  sed -i\".original\" '/def /a \\ \\ \\ \\ raise Fail(\"" + IDConstants.TEST_SERVICE_CHECK_INJECTED_FAILURE + "\")'  $file;  done;", getName()));
        }
        logger.logInfo("Failure injection complete on all hosts");
    }

    public void fixServiceCheckFailure() {
        for(String host : getHosts()) {
            LinuxCommandExecutor.executeCommand(host, "root", String.format("for file in `find /var/lib/ambari-agent/ -path */common-services/%s/*/service_check.py.original`;" +
                    " do  mv -f \"$file\" \"${file//\\.original/}\" ;  done;", getName()));
        }
    }

    public List<ServiceComponent> getSlaveComponents(){
        List<ServiceComponent> serviceComponents = new ArrayList<ServiceComponent>();

        for(ServiceComponent serviceComponent : getServiceComponents()) {
            if(serviceComponent.isSlaveComponent())
                serviceComponents.add(serviceComponent);
        }
        return serviceComponents;
    }

    public ServiceComponent getComponent(String componentName){
        for(ServiceComponent serviceComponent : getServiceComponents()) {
            if (serviceComponent.getName().equalsIgnoreCase(componentName))
                return serviceComponent;
        }
        return null;
    }

}
