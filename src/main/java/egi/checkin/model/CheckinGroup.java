package egi.checkin.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;


/**
 * Details of a group or virtual organisation (VO)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckinGroup {

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public long Id;

    @JsonProperty("ParentId")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public long parentId;

    @JsonProperty("CoId")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String coId;

    @JsonProperty("Name")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String name;

    @JsonProperty("Description")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String description;

    @JsonProperty("Created")
    @JsonFormat(pattern="yyyy-MM-dd' 'HH:mm:ss")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date created;

    @JsonProperty("Modified")
    @JsonFormat(pattern="yyyy-MM-dd' 'HH:mm:ss")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date modified;

    @JsonProperty("ActorIdentifier")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String modifiedBy;

    @JsonProperty("Version")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String version;

    @JsonProperty("Revision")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int revision;

    @JsonProperty("Deleted")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean deleted;
}
