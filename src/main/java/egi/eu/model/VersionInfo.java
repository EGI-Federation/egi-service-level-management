package egi.eu.model;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Date;

/**
 * Version number
 */
public abstract class VersionInfo {

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int version;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date changedOn;

    @Schema(description="User who created/updated this entity")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String changeBy;

    @Schema(description="Description of the change")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String changeDescription;
}