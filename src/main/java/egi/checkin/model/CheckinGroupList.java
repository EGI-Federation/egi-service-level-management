package egi.checkin.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;


/**
 * List of groups and/or virtual organisations (VOs)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckinGroupList {

    @JsonProperty("ResponseType")
    public String kind; // Cous

    @JsonProperty("Version")
    public String version;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("Cous")
    public List<CheckinGroup> groups;
}
