package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import egi.checkin.model.UserInfo;

import java.util.Date;
import java.util.List;


/**
 * Review of an entity
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Review<T> extends GenericEntity<T> {

    public String kind;

    public long id;

    public long reviewedEntityId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public T reviewedEntity;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date date;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<UserInfo> reviewers;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String reviewNotes;

    public boolean foundInconsistencies;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String reviewFollowUpActions;

    // Links
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<String> links  = null;


    /**
     * Constructor
     */
    public Review() {
        super(null, "Review", false);
    }

}
