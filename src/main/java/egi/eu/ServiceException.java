package egi.eu;

import io.smallrye.mutiny.tuples.Tuple2;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Exception class for IMS management tool
 */
public class ServiceException extends RuntimeException {

    private String id;
    private Map<String, String> details;


    /**
     * Construct with error id
     */
    public ServiceException(String id) {
        this.id = id;
    }

    /**
     * Construct with error id and message
     */
    public ServiceException(String id, String message) {
        super(message);
        this.id = id;
    }

    /**
     * Construct with error id and detail
     */
    public ServiceException(String id, Tuple2<String, String> detail) {
        this(id, Arrays.asList(detail));
    }

    /**
     * Construct with error id and details
     */
    public ServiceException(String id, List<Tuple2<String, String>> details) {
        this.id = id;
        this.details = new HashMap<>() {
            {
                for(Tuple2<String, String> detail : details)
                    if(null != detail.getItem2() && !detail.getItem2().isEmpty())
                        put(detail.getItem1(), detail.getItem2());
            }
        };
    }

    /**
     * Construct with exception and error id
     */
    public ServiceException(Throwable e, String id) {
        super(e);
        this.id = id;
    }

    /**
     * Construct with exception, error id and detail
     */
    public ServiceException(Throwable e, String id, Tuple2<String, String> detail) {
        this(e, id, Arrays.asList(detail));
    }

    /**
     * Construct with exception, error id and details
     */
    public ServiceException(Throwable e, String id, List<Tuple2<String, String>> details) {
        super(e);
        this.id = id;
        this.details = new HashMap<>() {
            {
                for(Tuple2<String, String> detail : details)
                    if(null != detail.getItem2() && !detail.getItem2().isEmpty())
                        put(detail.getItem1(), detail.getItem2());
            }
        };
    }

    public String getId() { return id; }

    public Map<String, String> getDetails() { return details; }
}
