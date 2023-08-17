package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Date;
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
        public static ProcessStatus of(int value) {
            return switch(value) {
                case 0 -> DRAFT;
                case 1 -> READY_FOR_APPROVAL;
                case 2 -> APPROVED;
                case 3 -> DEPRECATED;
                default -> null;
            };
        }
    }

    @Schema(enumeration={ "Process" })
    public String kind = "Process";

    public String code = PROCESS_CODE;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String goals;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String scope;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(format = "url")
    public String urlDiagram;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(format = "email")
    public String contact;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Set<Requirement> requirements;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Set<Interface> interfaces;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int reviewFrequency;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(enumeration={ "day", "month", "year" })
    public String frequencyUnit;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date nextReview;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public User approver;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date approvedOn;

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
        public HistoryOfProcess(List<Version<Process>> olderVersions) { super(olderVersions); }
    }

    /***
     * Constructor
     */
    public Process() {
        this(null, false, false);
    }

    /***
     * Copy constructor
     * @param process The entity to copy
     */
    public Process(ProcessEntity process) {
        this(process, false, false);
    }

    /***
     * Copy constructor
     * @param process The entity to copy
     */
    public Process(ProcessEntity process, boolean loadApiVersion, boolean storeVersion) {

        if(loadApiVersion) {
            final var config = ConfigProvider.getConfig();
            this.apiVersion = config.getValue("quarkus.smallrye-openapi.info-version", String.class);
        }

        if(null == process)
            return;

        this.goals = process.goals;
        this.scope = process.scope;
        this.urlDiagram = process.urlDiagram;
        this.reviewFrequency = process.reviewFrequency;
        this.frequencyUnit = process.frequencyUnit;
        this.nextReview = process.nextReview;
        if(null != process.approver)
            this.approver = new User(process.approver);
        this.approvedOn = process.approvedOn;
        this.status = ProcessStatus.of(process.status);

        if(null != process.requirements)
            this.requirements = process.requirements.stream().map(Process.Requirement::new).collect(Collectors.toSet());

        if(null != process.interfaces)
            this.interfaces = process.interfaces.stream().map(Process.Interface::new).collect(Collectors.toSet());

        if(storeVersion) {
            this.version = process.version;
            this.changedOn = process.changedOn;
            this.changeDescription = process.changeDescription;
            if(null != process.changeBy)
                this.changeBy = process.changeBy.fullName;
        }
    }

    /***
     * Construct from history.
     * @param processVersions The list of versions, should start with the latest version.
     */
    public Process(List<ProcessEntity> processVersions) {
        // Head of the list as the current version
        this(processVersions.get(0), true, true);

        // The rest of the list as the history of this entity
        var olderVersions = processVersions.stream().skip(1).map(entity -> {
                var process = new Process(entity);
                return new Version<>(process, entity.version, entity.changedOn, null != entity.changeBy ? entity.changeBy.fullName : null, entity.changeDescription);
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

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String code;

        public String requirement;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String source;

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

        /***
         * Constructor
         */
        public Interface() {}

        /***
         * Copy constructor
         */
        public Interface(ProcessEntity.Interface i) {
            this.direction = i.direction;
            this.description = i.description;
            this.relevantMaterial = i.relevantMaterial;
            this.interfacesWith = i.interfacesWith;
        }
    }
}
