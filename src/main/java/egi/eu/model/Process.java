package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import egi.checkin.model.UserInfo;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Date;
import java.util.List;


/***
 * Information about this IMS process
 */
public class Process {

    final static String PROCESS_CODE = "SLM";

    public enum Status {
        DRAFT,
        READY_FOR_APPROVAL,
        APPROVED,
        DEPRECATED
    }

    @Schema(enumeration={ "Process" })
    public String kind = "Process";

    @Schema(description="ID of the process, assigned on creation")
    public long id;

    public String code = PROCESS_CODE;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String goals;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String scope;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(format = "url")
    public String urlDiagram;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<Requirement> requirements;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<Interface> interfaces;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int reviewFrequency;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(enumeration={ "sec", "min", "hour", "day", "month", "year" })
    public String frequencyUnit;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date nextReview;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public UserInfo owner;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public UserInfo approver;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date approvedOn;

    public Status status;

    @Schema(description="The version of the process API")
    public String apiVersion;

    // Change history
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public HistoryOfProcess history = null;


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
        this.apiVersion = config.getValue("quarkus.smallrye-openapi.info-version", String.class);
    }


    /***
     * Some process requirement
     */
    public static class Requirement {

        @Schema(enumeration={ "Requirement" })
        public String kind = "Requirement";

        @Schema(description="ID of the requirement, assigned on creation")
        public long id;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String code;

        public String requirement;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String source;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public List<UserInfo> responsibles;
    }

    /***
     * Process input or output
     */
    public static class Interface {

        @Schema(enumeration={ "Interface" })
        public String kind = "Interface";

        @Schema(description="ID of the process interface, assigned on creation")
        long id;

        @Schema(enumeration={ "In", "Out" })
        public String direction;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String description;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String relevantMaterial;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @Schema(enumeration={ "Internal", "External", "Customer",
                "BA", "BDS", "CAPM", "CHM", "COM", "CONFM", "CSI", "CRM", "CPM", "FA", "HR", "ISM",
                "ISRM", "PPM", "PM", "PKM", "PPM", "RDM", "RM", "SACM", "SRM", "SLM", "SPM", "SRM" })
        public String interfacesWith;
    }
}
