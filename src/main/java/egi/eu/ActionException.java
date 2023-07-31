package egi.eu;

import io.smallrye.mutiny.tuples.Tuple2;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.*;


/**
 * Exception class for adapter operations
 */
public class ActionException extends RuntimeException {

    private String id;
    private Map<String, String> details;
    private Response.Status status = Response.Status.INTERNAL_SERVER_ERROR;;


    /**
     * Construct with error id
     */
    public ActionException(String id) {
        this.id = id;
    }

    /**
     * Construct with error id and message
     */
    public ActionException(String id, String message) {
        super(message);
        this.id = id;
    }

    /**
     * Construct with error id, message, and details
     */
    public ActionException(String id, String message, List<Tuple2<String, String>> details) {
        super(message);
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
     * Construct with error, message, and detail
     */
    public ActionException(String id, String message, Tuple2<String, String> detail) {
        this(id, message, Arrays.asList(detail));
    }

    /**
     * Construct with error id and details
     */
    public ActionException(String id, List<Tuple2<String, String>> details) {
        this(id, null, details);
    }

    /**
     * Construct with error id and detail
     */
    public ActionException(String id, Tuple2<String, String> detail) {
        this(id, null, Arrays.asList(detail));
    }

    /**
     * Construct with exception and error id
     */
    public ActionException(Throwable e, String id) {
        super(e);
        this.id = id;

        var type = e.getClass();
        if (ActionException.class.isAssignableFrom(type)) {
            // Build from action exception
            var ae = (ActionException)e;
            this.status = ae.status();
        }
        else if (WebApplicationException.class.isAssignableFrom(type)) {
            // Build from web exception
            var we = (WebApplicationException)e;
            this.status = Response.Status.fromStatusCode(we.getResponse().getStatus());
        }
    }

    /**
     * Construct with exception, error id and details
     */
    public ActionException(Throwable e, String id, List<Tuple2<String, String>> details) {
        this(e, id);
        this.details = new HashMap<>() {
            {
                for(Tuple2<String, String> detail : details)
                    if(null != detail.getItem2() && !detail.getItem2().isEmpty())
                        put(detail.getItem1(), detail.getItem2());
            }
        };
    }

    /**
     * Construct with exception, error id and detail
     */
    public ActionException(Throwable e, String id, Tuple2<String, String> detail) {
        this(e, id, Arrays.asList(detail));
    }

    /**
     * Construct with exception
     */
    public ActionException(Throwable e) {
        this(e, ActionException.class.isAssignableFrom(e.getClass()) ? ((ActionException)e).id() : "exception");

        // Don't forget to copy details
        if(ActionException.class.isAssignableFrom(e.getClass())) {
            this.details = new HashMap<>();
            this.details.putAll(((ActionException)e).details());
        }
    }

    /**
     * Construct with exception and details
     */
    public ActionException(Throwable e, List<Tuple2<String, String>> details) {
        this(e, ActionException.class.isAssignableFrom(e.getClass()) ? ((ActionException)e).id() : "exception", details);
    }

    /**
     * Construct with exception and detail
     */
    public ActionException(Throwable e, Tuple2<String, String> detail) {
        this(e, ActionException.class.isAssignableFrom(e.getClass()) ? ((ActionException)e).id() : "exception", detail);
    }

    /***
     * Retrieve the error id
     */
    public String id() { return this.id; }

    /***
     * Retrieve the details
     */
    public Map<String, String> details() { return this.details; }

    /***
     * Add detail
     */
    public ActionException detail(Tuple2<String, String> detail) {
        return details(Arrays.asList(detail));
    }

    /***
     * Add details
     */
    public ActionException details(List<Tuple2<String, String>> details) {
        if(null == this.details)
            this.details = new HashMap<>();

        for(Tuple2<String, String> detail : details)
            if(null != detail.getItem2() && !detail.getItem2().isEmpty())
                this.details.put(detail.getItem1(), detail.getItem2());

        return this;
    }

    /**
     * Retrieve the HTTP status code
     */
    public Response.Status status() {
        return this.status;
    }

    /**
     * Update the HTTP status code
     */
    public ActionException status(Response.Status status) {
        this.status = status;
        return this;
    }
}
