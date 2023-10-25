package egi.eu.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.smallrye.common.constraint.NotNull;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


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
    @JoinTable(name = "role_holder_map",
            joinColumns = { @JoinColumn(name = "role_id") },
            inverseJoinColumns = { @JoinColumn(name = "user_id") })
    public UserEntity user = null;

    @UpdateTimestamp
    public LocalDateTime changedOn;

    @ManyToOne(fetch = FetchType.EAGER,
            cascade = { CascadeType.PERSIST })
    @JoinTable(name = "role_assigner_map",
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
     * Get role logs older than the specified datetime, in reverse chronological order
     * @param role The role to fetch assignment logs for
     * @param from The date and time from where to start loading logs
     * @param limit The maximum number of logs to return
     * @return Role log entities
     */
    public static Uni<List<RoleLogEntity>> getRoleAssignments(String role, LocalDateTime from, int limit) {

        Map<String, Object> params = new HashMap<>();
        params.put("role", role);
        params.put("from", from);
        return find("role = :role AND changedOn < :from ORDER BY changedOn DESC", params)
                .page(Page.ofSize(limit))
                .list();
    }

}
