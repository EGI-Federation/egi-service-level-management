package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Version number
 */
public abstract class VersionInfo {

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int version;

    @Schema(description="Date and time of the last change. Assigned automatically, you should never send this.\n" +
                        "Always returned as UTC date and time.")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS", timezone = "UTC")
    public LocalDateTime changedOn; // UTC

    @Schema(description="User who created/updated this entity")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public User changeBy;

    @Schema(description="Description of the change")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String changeDescription;
}
