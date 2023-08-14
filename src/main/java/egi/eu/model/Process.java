package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

import java.util.Date;
import java.util.Set;


/***
 * Information about this IMS process
 */
@Entity
public class Process extends PanacheEntity {

    @Transient
    final static String PROCESS_CODE = "SLM";

    public enum ProcessStatus {
        DRAFT,
        READY_FOR_APPROVAL,
        APPROVED,
        DEPRECATED
    }

    @Transient
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

    @ManyToMany
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Set<Requirement> requirements;

    @ManyToMany
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Set<Interface> interfaces;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int reviewFrequency;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(enumeration={ "sec", "min", "hour", "day", "month", "year" })
    public String frequencyUnit;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date nextReview;

//    @ManyToOne
//    @JsonInclude(JsonInclude.Include.NON_EMPTY)
//    public User owner;
//
//    @ManyToOne
//    @JsonInclude(JsonInclude.Include.NON_EMPTY)
//    public User approver;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date approvedOn;

    public ProcessStatus status;

    @Schema(description="The version of the process API")
    public String apiVersion;

    // Change history
    @Transient
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
    @Entity
    @Table(name = "requirements")
    public static class Requirement extends PanacheEntity {

        @Transient
        @Schema(enumeration={ "Requirement" })
        public String kind = "Requirement";

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String code;

        public String requirement;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String source;

//        @OneToMany
//        @JsonInclude(JsonInclude.Include.NON_EMPTY)
//        public Set<User> responsibles;
    }

    /***
     * Process input or output
     */
    @Entity
    @Table(name = "interfaces")
    public static class Interface extends PanacheEntity {

        @Transient
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
    }
}
