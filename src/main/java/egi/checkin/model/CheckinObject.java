package egi.checkin.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Set;


/**
 * Details of an added/updated Check-in entity
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckinObject {

    @JsonProperty("ResponseType")
    public String kind; // NewObject, UpdatedObject

    @JsonProperty("Version")
    public String version = "1.0";

    @JsonProperty("ObjectType")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String type; // CoPersonRole

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String Id;

    @JsonProperty("InvalidFields")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, Set<String>> fieldErrors;
}
