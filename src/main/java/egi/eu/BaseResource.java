package egi.eu;

import egi.checkin.CheckinConfig;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import jakarta.inject.Inject;

import egi.checkin.Checkin;


/***
 * Base class for data transfer related resources.
 * Dynamically selects the appropriate data transfer service, depending on the desired destination.
 */
public class BaseResource {

    public static final String TEST_STUB = "x-test-stub";

    private Logger log;

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

}
