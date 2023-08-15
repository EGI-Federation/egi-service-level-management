package egi.eu.entity;

import io.quarkus.hibernate.reactive.panache.Panache;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.persistence.*;

import java.util.Date;
import java.util.List;
import java.util.Set;

import egi.eu.model.User;


/***
 * Information about this IMS process
 */
@Entity
@Table(name = "processes")
public class ProcessEntity extends PanacheEntity {

    @Column(length = 4096)
    public String goals;

    @Column(length = 4096)
    public String scope;

    @Schema(format = "url")
    public String urlDiagram;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "process_requirements",
               inverseJoinColumns = { @JoinColumn(name = "requirement_id") })
    public Set<Requirement> requirements = null;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "process_interfaces",
               inverseJoinColumns = { @JoinColumn(name = "interface_id") })
    public Set<Interface> interfaces = null;

    public int reviewFrequency = 1;

    @Schema(enumeration={ "day", "month", "year" })
    public String frequencyUnit = "year";

    public Date nextReview;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinTable(name = "process_owner",
               joinColumns = { @JoinColumn(name = "process_id") },
               inverseJoinColumns = { @JoinColumn(name = "user_id") })
    public User owner = null;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinTable(name = "process_approver",
               joinColumns = { @JoinColumn(name = "process_id") },
               inverseJoinColumns = { @JoinColumn(name = "user_id") })
    public User approver = null;

    public Date approvedOn;

    public int status;

    // Change tracking
    public int version = 1;
    public Date changeAt;
    public String changeDescription;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinTable(name = "process_editor",
            joinColumns = { @JoinColumn(name = "process_id") },
            inverseJoinColumns = { @JoinColumn(name = "user_id") })
    public User changeBy = null;


    /***
     * Constructor
     */
    public ProcessEntity() {}

    /***
     * Get the latest version
     * @return Latest version of the entity
     */
    public static Uni<List<ProcessEntity>> getLastVersion() {
        return findAll(Sort.by("version").descending()).range(0,0).list();
    }

    /***
     * Get all versions
     * @return All versions of the entity, sorted in reverse chronological order (head of the list is the latest).
     */
    public static Uni<List<ProcessEntity>> getAllVersions() {
        return findAll(Sort.by("version").descending()).list();
    }

    /***
     * Get all versions, paged
     * @return All versions of the entity, sorted in reverse chronological order (head of the list is the latest).
     */
    public static Uni<List<ProcessEntity>> getAllVersions(int index, int size) {
        return findAll(Sort.by("version").descending()).page(index, size).list();
    }

    /***
     * Some process requirement
     */
    @Entity
    @Table(name = "requirements")
    public static class Requirement extends PanacheEntity {

        @Column(length = 10)
        public String code;

        @Column(length = 2048)
        public String requirement;

        public String source;

        @OneToMany(fetch = FetchType.EAGER)
        @JoinTable(name = "requirement_responsibles",
                   joinColumns = { @JoinColumn(name = "requirement_id") },
                   inverseJoinColumns = { @JoinColumn(name = "user_id") })
        public Set<User> responsibles = null;
    }

    /***
     * Process input or output
     */
    @Entity
    @Table(name = "interfaces")
    public static class Interface extends PanacheEntity {

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
    }
}
