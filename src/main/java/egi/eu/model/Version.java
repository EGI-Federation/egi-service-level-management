package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.Date;


/**
 * Details of a version
 */
public class Version<T> extends GenericEntity<T> {

    public int version = 1;

    @Schema(description="Date and time of creation/update", example = "2022-10-15T20:14:22")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    Date changeAt;

    @Schema(description="User who created/updated this entity")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String changeBy;

    @Schema(description="Description of the change")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String changeDescription;

    public T entity;


    /**
     * Constructor
     */
    public Version() {
        super("Version",  null,false);
    }

    /**
     * Construct with versioned entity
     */
    public Version(T t, int version, Date changedAt, String changedBy, String changeDescription) {
        this();
        this.entity = t;
        this.version = version;
        this.changeAt = changedAt;
        this.changeBy = changedBy;
        this.changeDescription = changeDescription;

    }
}
