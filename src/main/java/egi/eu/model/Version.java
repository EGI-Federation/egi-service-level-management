package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.Date;


/**
 * Details of a version
 */
public abstract class Version<T> extends GenericEntity<T> {

    // Change tracking
    int version;

    @Schema(description="Date and time of creation/update", example = "2022-10-15T20:14:22")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    Date changeAt;

    @Schema(description="User who created/updated this entity")
    String changeBy;

    @Schema(description="Description of the change")
    String changeDescription;

    public T entity;


    /**
     * Constructor
     */
    public Version() {
        super("Version",  null,false);
    }
}
