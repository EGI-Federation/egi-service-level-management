package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

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

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String reviewNotes;

    boolean foundInconsistencies;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String reviewFollowUpActions;

    // Links
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<String> links  = null;


    /**
     * Constructor
     */
    public Review() {
        super(null, "Review", false);
    }

}
