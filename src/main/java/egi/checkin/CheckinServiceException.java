package egi.checkin;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;


/**
 * Exception class for Check-in API calls
 */
public class CheckinServiceException extends WebApplicationException {

    private String responseBody;

    public CheckinServiceException() {
        super();
    }

    public CheckinServiceException(Response resp, String body) {
        super(resp);
        this.responseBody = body;
    }

    String responseBody() { return responseBody; }
}
