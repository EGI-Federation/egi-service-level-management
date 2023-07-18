package egi.eu;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import jakarta.inject.Inject;
import java.net.MalformedURLException;
import java.net.URL;

import egi.checkin.CheckinConfig;
import egi.checkin.CheckinService;


/***
 * Base class for data transfer related resources.
 * Dynamically selects the appropriate data transfer service, depending on the desired destination.
 */
public class BaseResource {

    private Logger log;

    @Inject
    protected CheckinConfig oidc;

    @Inject
    protected IntegratedManagementSystemConfig config;

    protected static CheckinService checkin;


    /***
     * Construct with logger
     * @param log The logger (of subclass) to use
     */
    public BaseResource(Logger log) {
        this.log = log;
    }

    /**
     * Prepare REST client for EGI Check-in.
     * @return true on success
     */
    protected boolean getCheckinService() {

        if(null != checkin)
            return true;

        log.debug("Obtaining REST client for EGI Check-in");

        // Check if OIDC authentication URL is valid
        URL urlCheckin;
        try {
            urlCheckin = new URL(oidc.instance());
            urlCheckin = new URL(urlCheckin.getProtocol(), urlCheckin.getHost(), urlCheckin.getPort(), "");
        } catch (MalformedURLException e) {
            log.error(e.getMessage());
            return false;
        }

        try {
            // Create the REST client for EGI Check-in
            var rcb = RestClientBuilder.newBuilder().baseUrl(urlCheckin);
            checkin = rcb.build(CheckinService.class);

            return true;
        }
        catch(IllegalStateException ise) {
            log.error(ise.getMessage());
        }
        catch (RestClientDefinitionException rcde) {
            log.error(rcde.getMessage());
        }

        return false;
    }

    /***
     * Add information to the debug context.
     * @param key The key of the logged information
     * @param value The value of the logged information
     * @return Returns the logged value
     */
    protected Object addToDC(String key, Object value) {
        if(null != key && !key.isEmpty())
            MDC.put(key, null != value ? value : "null");

        return value;
    }

}
