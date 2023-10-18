package egi.checkin.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;


/**
 * List of membership records in a group or virtual organisations (VOs)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckinRoleList {

    @JsonProperty("RequestType")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String requestType; // CoPersonRoles

    @JsonProperty("ResponseType")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String kind; // CoPersonRoles

    @JsonProperty("Version")
    public String version = "1.0";

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("CoPersonRoles")
    public List<CheckinRole> records;


    /***
     * Constructor
     */
    public CheckinRoleList() {}

    /***
     * Construct as request to add/update membership record
     */
    public CheckinRoleList(String checkinUserId, String groupName, String coId, String affiliation, String status) {
        this(checkinUserId, groupName, coId, null, affiliation, status);
    }

    /***
     * Construct as request to add/update role record
     */
    public CheckinRoleList(String checkinUserId, String groupName, String coId, String title, String affiliation, String status) {
        this.requestType = "CoPersonRoles";
        this.records = new ArrayList<>();

        var record = new CheckinRole(checkinUserId, groupName, coId, affiliation, status);
        if(null != title)
            record.title = title;
        this.records.add(record);
    }
}
