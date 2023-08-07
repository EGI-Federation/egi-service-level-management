package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Date;
import java.util.List;


/***
 * Information about this IMS process
 */
public class Process {

    final static String PROCESS_CODE = "SLM";

    @Schema(enumeration={ "Process" })
    public String kind = "Process";

    public String code = PROCESS_CODE;
    public String softwareVersion;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String goals;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String scope;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(format = "url")
    public String urlDiagram;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<Requirement> requirements;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<Interface> interfaces;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int reviewFrequency;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(enumeration={ "sec", "min", "hour", "day", "month", "year" })
    public String frequencyUnit;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date nextReview;

    // Change history
    @JsonInclude(JsonInclude.Include.NON_NULL)
    HistoryOfProcess history = null;


    /***
     * History of the process details
     */
    public static class HistoryOfProcess extends History<Process> {
        public HistoryOfProcess() { super(); }
    }

    /***
     * Constructor
     */
    public Process() {
        final var config = ConfigProvider.getConfig();
        this.softwareVersion = config.getValue("quarkus.smallrye-openapi.info-version", String.class);
    }
}
