package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import egi.eu.entity.ProcessEntity;


/***
 * Information about this IMS process
 */
public class Process extends VersionInfo {

    final static String PROCESS_CODE = "SLM";

    public enum ProcessStatus {
        DRAFT(0),
        READY_FOR_APPROVAL(1),
        APPROVED(2),
        DEPRECATED(3);

        private final int value;
        private ProcessStatus(int value) { this.value = value; }
        public int getValue() { return value; }
        public static ProcessStatus of(int value) {
            return switch(value) {
                case 1 -> READY_FOR_APPROVAL;
                case 2 -> APPROVED;
                case 3 -> DEPRECATED;
                default -> DRAFT;
            };
        }
    }

    @Schema(enumeration={ "Process" })
    public String kind = "Process";

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Long id;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String code = PROCESS_CODE;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String goals; // Markdown

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String scope; // Markdown

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(format = "url")
    public String urlDiagram;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(format = "email")
    public String contact;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Set<Requirement> requirements;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Set<Interface> interfaces;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int reviewFrequency;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(enumeration={ "day", "month", "year" })
    public String frequencyUnit;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public LocalDateTime nextReview;

    public ProcessStatus status = ProcessStatus.DRAFT;

    @Schema(description="The version of the process API")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String apiVersion;

    // Change history
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public HistoryOfProcess history = null;


    /***
     * History of the process details
     */
    public static class HistoryOfProcess extends History<Process> {
        public HistoryOfProcess() { super(); }
        public HistoryOfProcess(List<Process> olderVersions) { super(olderVersions); }
    }

    /***
     * Constructor
     */
    public Process() {
        this(null, false);
    }

    /***
     * Copy constructor
     * @param process The entity to copy
     */
    public Process(ProcessEntity process) {
        this(process, false);
    }

    /***
     * Copy constructor
     * @param process The entity to copy
     */
    public Process(ProcessEntity process, boolean loadApiVersion) {

        if(loadApiVersion) {
            final var config = ConfigProvider.getConfig();
            this.apiVersion = config.getValue("quarkus.smallrye-openapi.info-version", String.class);
        }

        if(null == process)
            return;

        this.id = process.id;
        this.goals = process.goals;
        this.scope = process.scope;
        this.urlDiagram = process.urlDiagram;
        this.contact = process.contact;
        this.reviewFrequency = process.reviewFrequency;
        this.frequencyUnit = process.frequencyUnit;
        this.nextReview = process.nextReview;
        this.status = ProcessStatus.of(process.status);

        if(null != process.requirements)
            this.requirements = process.requirements.stream().map(Process.Requirement::new).collect(Collectors.toSet());

        if(null != process.interfaces)
            this.interfaces = process.interfaces.stream().map(Process.Interface::new).collect(Collectors.toSet());

        this.version = process.version;
        this.changedOn = process.changedOn;
        this.changeDescription = process.changeDescription;
        if(null != process.changeBy)
            this.changeBy = new User(process.changeBy);
    }

    /***
     * Construct from history.
     * @param processVersions The list of versions, should start with the latest version.
     */
    public Process(List<ProcessEntity> processVersions) {
        // Head of the list as the current version
        this(processVersions.get(0), true);

        // The rest of the list as the history of this entity
        var olderVersions = processVersions.stream().skip(1).map(entity -> {
                return new Process(entity, false);
            }).toList();

        if(!olderVersions.isEmpty())
            this.history = new HistoryOfProcess(olderVersions);
    }


    /***
     * Some process requirement
     */
    public static class Requirement {

        @Schema(enumeration={ "Requirement" })
        public String kind = "Requirement";

        public Long id;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String code;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String requirement; // Markdown

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String source; // Markdown

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public Set<User> responsibles;


        /***
         * Constructor
         */
        public Requirement() {}

        /***
         * Copy constructor
         */
        public Requirement(ProcessEntity.Requirement requirement) {
            this.id = requirement.id;
            this.code = requirement.code;
            this.requirement = requirement.requirement;
            this.source = requirement.source;

            if(null != requirement.responsibles)
                this.responsibles = requirement.responsibles.stream().map(User::new).collect(Collectors.toSet());
        }
    }

    /***
     * Process input or output
     */
    public static class Interface {

        @Schema(enumeration={ "Interface" })
        public String kind = "Interface";

        public Long id;

        @Schema(enumeration={ "In", "Out" })
        public String direction;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String description; // Markdown

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String relevantMaterial; // Markdown

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @Schema(enumeration={ "Internal", "External", "Customer",
                "BA", "BDS", "CAPM", "CHM", "COM", "CONFM", "CSI", "CRM", "CPM", "FA", "HR", "ISM",
                "ISRM", "PPM", "PM", "PKM", "PPM", "RDM", "RM", "SACM", "SRM", "SLM", "SPM", "SRM" })
        public String interfacesWith;

        /***
         * Constructor
         */
        public Interface() {}

        /***
         * Copy constructor
         */
        public Interface(ProcessEntity.Interface i) {
            this.id = i.id;
            this.direction = i.direction;
            this.description = i.description;
            this.relevantMaterial = i.relevantMaterial;
            this.interfacesWith = i.interfacesWith;
        }
    }
}
