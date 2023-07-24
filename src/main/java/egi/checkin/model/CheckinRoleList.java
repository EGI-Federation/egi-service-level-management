package egi.checkin.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;


/**
 * List of membership records in a group or virtual organisations (VOs)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckinRoleList {

    @JsonProperty("ResponseType")
    public String kind; // CoPersonRoles

    @JsonProperty("Version")
    public String version;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("CoPersonRoles")
    public List<CheckinRole> records;
}
