package egi.eu;

import java.util.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import egi.checkin.CheckinServiceException;


/**
 * An error in an operation of the REST API.
 * Can be constructed manually or from exceptions.
 *
 * This is the OpenApi entity that will be returned in case of error in the API.
 * The HTTP status will be returned as the status code of the failed endpoint and the other
 * members will be rendered as the body of the response of the failed endpoint.
 */
@Schema(name = "Error")
public class ActionError {

    @Schema(description="Error type")
    public String id;

    @Schema(description="Error message")
    @JsonInclude(Include.NON_EMPTY)
    public String description;

    @Schema(description="Additional details about the error")
    @JsonInclude(Include.NON_EMPTY)
    public Map<String, String> details;

    @JsonIgnore
    private Status status = defaultStatus();

    public static Status defaultStatus() { return Status.INTERNAL_SERVER_ERROR; }


    /**
     * Copy constructor does deep copy
     * @param error Error to copy
     */
    public ActionError(ActionError error) {

        this.id = error.id;
        this.status = error.status();
        this.description = error.description;

        if(null != error.details) {
            var ed = error.details;
            if(!ed.isEmpty())
                this.details = new HashMap<>(ed);
        }
    }

    /**
     * Copy but change id. Status code stays the same.
     * @param error Error to copy
     * @param newId New error id
     */
    public ActionError(ActionError error, String newId) {
        this(error);
        this.id = newId;
    }

    /**
     * Construct with error id
     * @param id Error id
     */
    public ActionError(String id) {
        this.id = id;

        updateStatusFromId();
    }

    /**
     * Construct with error id and description
     * @param id Error id
     * @param description Error description
     */
    public ActionError(String id, String description) {
        this.id = id;

        updateStatusFromId();
    }

    /**
     * Construct with error id and detail
     * @param id Error id
     * @param detail Key-value pair to add to the details of the error
     */
    public ActionError(String id, Tuple2<String, String> detail) {
        this(id, null, Arrays.asList(detail));
    }

    /**
     * Construct with error id and details
     * @param id Error id
     * @param details Key-value pairs to add to the details of the error
     */
    public ActionError(String id, List<Tuple2<String, String>> details) {
        this(id, null, details);
    }

    /**
     * Construct with error id, description, and detail
     * @param id Error id
     * @param description Error description
     * @param detail Key-value pair to add to the details of the error
     */
    public ActionError(String id, String description, Tuple2<String, String> detail) {
        this(id, description, Arrays.asList(detail));
    }

    /**
     * Construct with error id, description, and details
     * @param id Error id
     * @param description Error description
     * @param details Key-value pairs to add to the details of the error
     */
    public ActionError(String id, String description, List<Tuple2<String, String>> details) {
        this.id = id;
        this.description = description;
        this.details = new HashMap<>() {
            {
                for(Tuple2<String, String> detail : details)
                    if(null != detail.getItem2() && !detail.getItem2().isEmpty())
                        put(detail.getItem1(), detail.getItem2());
            }
        };

        updateStatusFromId();
    }

    /**
     * Construct from exception
     * @param t The exception to wrap
     */
    public ActionError(Throwable t) {
        this.id = "exception";

        String msg = t.getMessage();
        if(null != msg && !msg.isEmpty())
            this.description = msg;

        var type = t.getClass();
        if (type.equals(CheckinServiceException.class) ||
            type.equals(ClientWebApplicationException.class) ||
            type.equals(WebApplicationException.class) ) {
            // Build from web exception
            var we = (WebApplicationException)t;
            this.status = Status.fromStatusCode(we.getResponse().getStatus());

            if(this.id.equals("exception"))
                updateIdFromStatus();

            if(this.description.isEmpty()) {
                String reason = we.getResponse().getStatusInfo().getReasonPhrase();
                if (null != reason && !reason.isEmpty())
                    this.description = reason;
            }
        }
        else if (type.equals(ActionException.class)) {
            ActionException ae = (ActionException)t;
            this.id = ae.id();

            if(!updateStatusFromId())
                this.status = ae.status();

            // Collect the details from the exception (if any)
            var aeDetails = ae.details();
            if(null != aeDetails && !aeDetails.isEmpty())
                this.details = new HashMap<>(aeDetails);
        }
    }

    /**
     * Construct from exception and detail
     * @param t The exception to wrap
     * @param detail Key-value pair to add to the details of the error
     */
    public ActionError(Throwable t, Tuple2<String, String> detail) {
        this(t, Arrays.asList(detail));
    }

    /**
     * Construct from exception and details
     * @param t The exception to wrap
     * @param details Key-value pair to add to the details of the error
     */
    public ActionError(Throwable t, List<Tuple2<String, String>> details) {
        this(t);

        // Combine details (copied from throwable) with the extra ones
        Map<String, String> combinedDetails = new HashMap<>();
        for(Tuple2<String, String> detail : details)
            if(null != detail.getItem2() && !detail.getItem2().isEmpty())
                combinedDetails.put(detail.getItem1(), detail.getItem2());

        if(null != this.details && !this.details.isEmpty())
            combinedDetails.putAll(this.details);

        this.details = combinedDetails;
    }

    /***
     * Determine id from status code
     * @return True if id was updated
     */
    private boolean updateIdFromStatus() {
        switch (this.status) {
            case UNAUTHORIZED -> this.id = "notAuthorized";
            case FORBIDDEN -> this.id = "noAccess";
            case BAD_REQUEST -> this.id = "badRequest";
            case NOT_FOUND -> this.id = "notFound";
            default -> {
                return false;
            }
        }

        return true;
    }

    /***
     * Determine status code from id
     * @return True if status code was updated
     */
    private boolean updateStatusFromId() {
        switch (this.id) {
            case "notAuthorized" -> this.status = Status.UNAUTHORIZED;
            case "noAccess" -> this.status = Status.FORBIDDEN;
            case "badRequest" -> this.status = Status.BAD_REQUEST;
            case "notFound" -> this.status = Status.NOT_FOUND;
            default -> {
                return false;
            }
        }

        return true;
    }

    /**
     * Retrieve the HTTP status code
     * @return HTTP status code
     */
    public Status status() {
        return this.status;
    }

    /**
     * Update the HTTP status code
     * @param status New HTTP status
     * @return Instance to allow for fluent calls (with .)
     */
    public ActionError status(Status status) {
        this.status = status;
        return this;
    }

    /**
     * Convert to Response that can be returned by a REST endpoint
     * @return Response object
     */
    public Response toResponse() {
        return Response.ok(this).status(this.status).build();
    }

    /**
     * Convert to Response with new status that can be returned by a REST endpoint
     * @param status New HTTP status
     * @return Response object with new HTTP status code
     */
    public Response toResponse(Status status) {
        return Response.ok(this).status(status).build();
    }
}
