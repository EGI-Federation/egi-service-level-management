package egi.checkin.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;


/**
 * Details of a group or virtual organisation (VO)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckinGroup {

    public long Id;

    @JsonProperty("ParentId")
    public long parentId;

    @JsonProperty("CoId")
    public String coId;

    @JsonProperty("Name")
    public String name;

    @JsonProperty("Description")
    public String description;

    @JsonProperty("Created")
    @JsonFormat(pattern="yyyy-MM-dd' 'HH:mm:ss")
    public Date created;

    @JsonProperty("Modified")
    @JsonFormat(pattern="yyyy-MM-dd' 'HH:mm:ss")
    public Date modified;

    @JsonProperty("ActorIdentifier")
    public String modifiedBy;

    @JsonProperty("Version")
    public String version;

    @JsonProperty("Revision")
    public int revision;

    @JsonProperty("Deleted")
    public boolean deleted;
}
