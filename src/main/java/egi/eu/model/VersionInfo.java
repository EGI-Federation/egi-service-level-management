package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


/**
 * Version number
 */
public abstract class VersionInfo {

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int version;

    @Schema(description="Date and time of the last change. Assigned automatically, you should never send this.\n" +
                        "Always returned as UTC date and time.")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonSerialize(using = UtcLocalDateTimeSerializer.class)
    public LocalDateTime changedOn; // UTC

    @Schema(description="User who created/updated this entity")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public User changeBy;

    @Schema(description="Description of the change")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String changeDescription;

    // Custom JSON serializer for date/time
    public static class UtcLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {

        private static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");

        @Override
        public void serialize(LocalDateTime someDateTime,
                              JsonGenerator jsonGenerator,
                              SerializerProvider serializerProvider)
                throws IOException, JsonProcessingException {
            // As all date/times we return are in UTC, add a Z to the configured formatting
            var stringDateTime = someDateTime.format(dtf);
            jsonGenerator.writeObject(stringDateTime + 'Z');
        }
    }
}
