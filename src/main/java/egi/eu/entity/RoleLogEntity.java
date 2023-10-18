package egi.eu.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import io.smallrye.common.constraint.NotNull;
import io.smallrye.mutiny.Uni;
import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;


/**
 * Role assignment or revocation
 */
@Entity
@Table(name = "rolelog")
public class RoleLogEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(length = 50)
    @NotNull
    public String role;

    public boolean assigned;

    @ManyToOne(fetch = FetchType.EAGER,
            cascade = { CascadeType.PERSIST })
    @JoinTable(name = "role_holder",
            joinColumns = { @JoinColumn(name = "role_id") },
            inverseJoinColumns = { @JoinColumn(name = "user_id") })
    public UserEntity user = null;

    @UpdateTimestamp
    public LocalDateTime changedOn;

    @ManyToOne(fetch = FetchType.EAGER,
            cascade = { CascadeType.PERSIST })
    @JoinTable(name = "role_assigner",
            joinColumns = { @JoinColumn(name = "role_id") },
            inverseJoinColumns = { @JoinColumn(name = "user_id") })
    public UserEntity changeBy = null;


    /***
     * Constructor
     */
    public RoleLogEntity() { super(); }

    /***
     * Copy constructor
     * @param role The role assigned/revoked
     * @param assigned Shows if the role was assigned or revoked
     * @param user The user the role was assigned to or revoked from
     * @param changeBy The caller user
     */
    public RoleLogEntity(String role, boolean assigned, UserEntity user, UserEntity changeBy) {
        super();

        // Copy simple fields
        this.role = role;
        this.assigned = assigned;
        this.user = user;
        this.changeBy = changeBy;
    }

    /***
     * Get all versions of all roles
     * @return All role entities, sorted in reverse chronological order (head of the list is the latest).
     */
    public static Uni<List<RoleLogEntity>> getRoleAssignments() {
        return findAll(Sort.by("changedOn").descending()).list();
    }

}
