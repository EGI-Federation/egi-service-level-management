package egi.eu.entity;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.hibernate.annotations.UpdateTimestamp;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import egi.eu.model.Process;
import egi.eu.model.Process.ProcessStatus;
import egi.eu.Utils;


/***
 * Information about this IMS process
 */
@Entity
@Table(name = "process")
public class ProcessEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(length = 4096)
    public String goals;

    @Column(length = 4096)
    public String scope;

    @Schema(format = "url")
    public String urlDiagram;

    public String contact;

    @ManyToMany(fetch = FetchType.EAGER,
                cascade = { CascadeType.PERSIST })
    @JoinTable(name = "process_requirements_map",
               joinColumns = { @JoinColumn(name = "process_id") },
               inverseJoinColumns = { @JoinColumn(name = "requirement_id") })
    public Set<Requirement> requirements = null;

    @ManyToMany(fetch = FetchType.EAGER,
                cascade = { CascadeType.PERSIST })
    @JoinTable(name = "process_interfaces_map",
               joinColumns = { @JoinColumn(name = "process_id") },
               inverseJoinColumns = { @JoinColumn(name = "interface_id") })
    public Set<Interface> interfaces = null;

    public int reviewFrequency = 1;

    @Schema(enumeration={ "day", "month", "year" })
    @Column(length = 10)
    public String frequencyUnit = "year";

    public LocalDateTime nextReview;

    public int status;

    // Change tracking
    @Column(nullable = false, unique = true, insertable = false, updatable = false, columnDefinition = "serial")
    public int version;

    @UpdateTimestamp
    public LocalDateTime changedOn;

    @Column(length = 1024)
    public String changeDescription;

    @ManyToOne(fetch = FetchType.EAGER,
               cascade = { CascadeType.PERSIST })
    @JoinTable(name = "process_editor_map",
            joinColumns = { @JoinColumn(name = "process_id") },
            inverseJoinColumns = { @JoinColumn(name = "user_id") })
    public UserEntity changeBy = null;


    /***
     * Constructor
     */
    public ProcessEntity() { super(); }

    /***
     * Copy constructor with new status
     * @param process The process to copy
     * @param newStatus The new status
     */
    public ProcessEntity(ProcessEntity process, Process.ProcessStatus newStatus) {
        super();

        // Copy simple fields
        this.goals = process.goals;
        this.scope = process.scope;
        this.contact = process.contact;
        this.reviewFrequency = process.reviewFrequency;
        this.frequencyUnit = process.frequencyUnit;
        this.nextReview = process.nextReview;
        this.urlDiagram = process.urlDiagram;
        this.status = newStatus.getValue();

        // Copy requirements
        if(null != process.requirements) {
            this.requirements = new HashSet<>();
            this.requirements.addAll(process.requirements);
        }

        // Copy interfaces
        if(null != process.interfaces) {
            this.interfaces = new HashSet<>();
            this.interfaces.addAll(process.interfaces);
        }
    }

    /***
     * Copy constructor
     * @param process The new version (from the frontend)
     * @param latest The latest version in the database
     * @param users The users that already exist in the database
     */
    public ProcessEntity(Process process, ProcessEntity latest, Map<String, UserEntity> users) {
        super();

        // Copy simple fields
        this.changeDescription = process.changeDescription;
        if(null != process.changeBy) {
            if(null != users && users.containsKey(process.changeBy.checkinUserId))
                this.changeBy = users.get(process.changeBy.checkinUserId);
            else
                this.changeBy = new UserEntity(process.changeBy);
        }

        this.goals = process.goals;
        this.scope = process.scope;
        this.contact = process.contact;
        this.reviewFrequency = process.reviewFrequency;
        this.frequencyUnit = process.frequencyUnit;
        this.nextReview = process.nextReview;
        this.urlDiagram = process.urlDiagram;

        final var latestStatus = ProcessStatus.of(latest.status);
        if(ProcessStatus.APPROVED == latestStatus)
            // Changing an approved process will require a new approval
            this.status = ProcessStatus.DRAFT.getValue();
        else
            this.status = latestStatus.getValue();

        // Link to the requirements that stayed the same, create new ones for the ones that changed
        if(null != process.requirements) {
            this.requirements = new HashSet<>();
            for(var reqe : process.requirements) {
                // See if there is such a requirement in the latest version
                ProcessEntity.Requirement reql = null;
                if(null != latest.requirements)
                    for(var req : latest.requirements)
                        if(req.id.equals(reqe.id)) {
                            reql = req;
                            break;
                        }

                if(null == reql) {
                    // This is a new requirement
                    this.requirements.add(new Requirement(reqe, users));
                    continue;
                }

                // See if this requirement has changed
                boolean hasChanged = (
                        !Utils.equalStrings(reqe.code, reql.code) ||
                        !Utils.equalStrings(reqe.requirement, reql.requirement) ||
                        !Utils.equalStrings(reqe.source, reql.source) ||
                       (null == reqe.responsibles) != (null == reql.responsibles));

                if(!hasChanged && null != reqe.responsibles) {
                    if(reqe.responsibles.size() != reql.responsibles.size())
                        hasChanged = true;
                    else {
                        var responsibles = new HashSet<String>(); // Responsible users in the latest version
                        for(var resp : reql.responsibles)
                            responsibles.add(resp.checkinUserId);
                        for(var resp : reqe.responsibles)
                            if(!responsibles.contains(resp.checkinUserId)) {
                                hasChanged = true;
                                break;
                            }
                    }
                }

                if(hasChanged)
                    this.requirements.add(new Requirement(reqe, users));
                else
                    this.requirements.add(reql);
            }
        }

        // Link to the interfaces that stayed the same, create new ones for the ones that changed
        if(null != process.interfaces) {
            this.interfaces = new HashSet<>();
            for(var itfe : process.interfaces) {
                // See if there is such an interface in the latest version
                ProcessEntity.Interface itfl = null;
                if(null != latest.interfaces)
                    for(var itf : latest.interfaces)
                        if(itf.id.equals(itfe.id)) {
                            itfl = itf;
                            break;
                        }

                if(null == itfl) {
                    // This is a new interface
                    this.interfaces.add(new Interface(itfe));
                    continue;
                }

                // See if this interface has changed
                boolean hasChanged = (
                        !Utils.equalStrings(itfe.direction, itfl.direction) ||
                        !Utils.equalStrings(itfe.description, itfl.description) ||
                        !Utils.equalStrings(itfe.relevantMaterial, itfl.relevantMaterial) ||
                        !Utils.equalStrings(itfe.interfacesWith, itfl.interfacesWith));

                if(hasChanged)
                    this.interfaces.add(new Interface(itfe));
                else
                    this.interfaces.add(itfl);
            }
        }
    }

    /***
     * Get the latest version as a list with one element
     * @return List with latest version of the entity
     */
    public static Uni<List<ProcessEntity>> getLastVersionAsList() {
        return find("ORDER BY version DESC").range(0,0).list();
    }

    /***
     * Get the latest version
     * @return Latest version of the entity
     */
    public static Uni<ProcessEntity> getLastVersion() {
        return find("ORDER BY version DESC").firstResult();
    }

    /***
     * Get all versions
     * @return All versions of the entity, sorted in reverse chronological order (head of the list is the latest).
     */
    public static Uni<List<ProcessEntity>> getAllVersions() {
        return find("ORDER BY version DESC").list();
    }

    /***
     * Get all versions, paged
     * @return All versions of the entity, sorted in reverse chronological order (head of the list is the latest).
     */
    public static Uni<List<ProcessEntity>> getAllVersions(int index, int size) {
        return find("ORDER BY version DESC").page(index, size).list();
    }

    /***
     * Some process requirement
     */
    @Entity
    @Table(name = "process_requirements")
    public static class Requirement extends PanacheEntityBase {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        public Long id;

        @Column(length = 10)
        public String code;

        @Column(length = 2048)
        public String requirement;

        @Column(length = 1024)
        public String source;

        @ManyToMany(fetch = FetchType.EAGER,
                    cascade = { CascadeType.PERSIST })
        @JoinTable(name = "process_requirement_responsibles_map",
                   joinColumns = { @JoinColumn(name = "requirement_id") },
                   inverseJoinColumns = { @JoinColumn(name = "user_id") })
        public Set<UserEntity> responsibles = null;

        /***
         * Constructor
         */
        public Requirement() { super(); }

        /***
         * Copy constructor
         */
        public Requirement(Process.Requirement requirement, Map<String, UserEntity> users) {
            super();

            this.code = requirement.code;
            this.requirement = requirement.requirement;
            this.source = requirement.source;

            if(null != requirement.responsibles) {
                this.responsibles = new HashSet<>();
                for(var user : requirement.responsibles) {
                    if(users.containsKey(user.checkinUserId)) {
                        // User already exists in the database
                        var userEntity = users.get(user.checkinUserId);
                        this.responsibles.add(userEntity);
                    }
                    else {
                        // New user
                        var userEntity = new UserEntity(user);
                        this.responsibles.add(userEntity);
                    }
                }
            }
        }
    }

    /***
     * Process input or output
     */
    @Entity
    @Table(name = "process_interfaces")
    public static class Interface extends PanacheEntityBase {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        public Long id;

        @Schema(enumeration={ "In", "Out" })
        @Column(length = 5)
        public String direction;

        @Column(length = 2048)
        public String description;

        @Column(length = 2048)
        public String relevantMaterial;

        @Schema(enumeration={ "Internal", "External", "Customer",
                "BA", "BDS", "CAPM", "CHM", "COM", "CONFM", "CSI", "CRM", "CPM", "FA", "HR", "ISM",
                "ISRM", "PPM", "PM", "PKM", "PPM", "RDM", "RM", "SACM", "SRM", "SLM", "SPM", "SRM" })
        public String interfacesWith;

        /***
         * Constructor
         */
        public Interface() { super(); }

        /***
         * Copy constructor
         */
        public Interface(Process.Interface itf) {
            super();

            this.direction = itf.direction;
            this.description = itf.description;
            this.relevantMaterial = itf.relevantMaterial;
            this.interfacesWith = itf.interfacesWith;
        }
    }
}
