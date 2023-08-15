package egi.eu;

import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;

import egi.checkin.Checkin;


/***
 * Base class for data transfer related resources.
 * Dynamically selects the appropriate data transfer service, depending on the desired destination.
 */
public class BaseResource {

    public static final String TEST_STUB = "x-test-stub";

    protected Logger log;
    protected Checkin checkin;


    /***
     * Construct with logger
     * @param log The logger (of subclass) to use
     */
    public BaseResource(Logger log) {
        this.log = log;
        this.checkin = new Checkin();
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

    /***
     * Helper to obtain the original request URI, even when running behind a reverse proxy.
     * Note that the proxy must forward the original request path in the HTTP header X-Real-Path.
     * @param uriInfo Details of the request URI
     * @param httpHeaders Request HTTP headers
     * @return
     */
    protected URI getRealRequestUri(UriInfo uriInfo, HttpHeaders httpHeaders) {
        var uri = uriInfo.getRequestUri();
        var header = httpHeaders.getRequestHeader("X-Real-Path");
        if(null != header && !header.isEmpty()) {
            var path = header.get(0);
            var query = uri.getQuery();
            var queryIndex = path.indexOf('?');
            if(queryIndex > 0)
                path = path.substring(0, queryIndex);

            uri = UriBuilder.fromUri(uri).replacePath("").path(path).replaceQuery(query).build();
        }

        return uri;
    }

}
