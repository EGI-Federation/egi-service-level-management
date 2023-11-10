package egi.eu.entity;

import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.reactive.mutiny.Mutiny;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.smallrye.common.constraint.NotNull;
import io.smallrye.mutiny.Uni;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.*;

import egi.eu.model.Role;
import egi.eu.model.Role.RoleStatus;


/**
 * Details of a role
 */
@Entity
@Table(name = "roles")
public class RoleEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(length = 50)
    @NotNull
    public String role;

    @Column(length = 50)
    @NotNull
    public String name; // Human-readable version of the role field

    @Column(length = 4096)
    public String tasks; // Markdown

    @Column(length = 50)
    public String globalRole;

    @Column(length = 50)
    public String globalRoleName;

    @Column(length = 4096)
    public String globalRoleTasks; // Markdown

    public boolean handover;

    public int status;

    // Change tracking
    public int version = 1;

    @UpdateTimestamp
    public LocalDateTime changedOn;

    @Column(length = 2048)
    public String changeDescription;

    @ManyToOne(fetch = FetchType.EAGER,
            cascade = { CascadeType.PERSIST })
    @JoinTable(name = "role_editor_map",
            joinColumns = { @JoinColumn(name = "role_id") },
            inverseJoinColumns = { @JoinColumn(name = "user_id") })
    public UserEntity changeBy = null;


    /***
     * Constructor
     */
    public RoleEntity() {
        super();

        this.handover = false;
    }

    /***
     * Copy constructor with new status
     * @param role The role to copy
     * @param newStatus The new status
     */
    public RoleEntity(RoleEntity role, RoleStatus newStatus) {
        super();

        // Copy simple fields
        this.role = role.role;
        this.name = role.name;
        this.tasks = role.tasks;
        this.globalRole = role.globalRole;
        this.globalRoleName = role.globalRoleName;
        this.globalRoleTasks = role.globalRoleTasks;
        this.handover = role.handover;
        this.status = newStatus.getValue();

        this.version = role.version + 1;
    }

    /***
     * Copy constructor
     * @param role The new version (from the frontend)
     * @param latest The latest version in the database
     * @param user The caller user that already exist in the database
     */
    public RoleEntity(Role role, RoleEntity latest, UserEntity user) {
        super();

        // Copy simple fields
        this.changeDescription = role.changeDescription;
        this.version = null == latest ? 1 : latest.version + 1;
        if(null != role.changeBy) {
            if(null != user && role.changeBy.checkinUserId.equals(user.checkinUserId))
                this.changeBy = user;
            else
                this.changeBy = new UserEntity(role.changeBy);
        }

        this.role = role.role;
        this.name = role.name;
        this.tasks = role.tasks;
        this.globalRole = role.globalRole;
        this.globalRoleName = role.globalRoleName;
        this.globalRoleTasks = role.globalRoleTasks;
        this.handover = role.handover;

        if(null == latest)
            this.status = RoleStatus.DRAFT.getValue();
        else {
            final var latestStatus = RoleStatus.of(latest.status);
            if(RoleStatus.IMPLEMENTED == latestStatus)
                // Changing an implemented role will require a new implementation
                this.status = RoleStatus.DRAFT.getValue();
            else
                this.status = latestStatus.getValue();
        }
    }

    /***
     * Get only the last versions (without history)
     * @return All role entities, sorted in reverse chronological order (head of the list is the latest).
     */
    public static Uni<List<RoleEntity>> getAllRoles(Mutiny.Session session) {
        final String sql = """
            SELECT id,role,name,version,status,handover,tasks,globalrole,globalrolename,globalroletasks,
                   changedon,changedescription FROM slm.roles\s
            JOIN (
                SELECT DISTINCT ON (role)\s
                    last_value(id) OVER wnd AS last_id\s
                FROM slm.roles WINDOW wnd AS (\s
                    PARTITION BY role ORDER BY version ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING\s
                )\s
            ) AS last\s
                ON id = last_id\s
            LEFT JOIN slm.role_editor_map editors\s
                ON id = editors.role_id
            """;

        var query = session.createNativeQuery(sql, RoleEntity.class);
        return query.getResultList();
    }

    /***
     * Get all versions of all roles
     * @return All role entities, sorted in reverse chronological order (head of the list is the latest).
     */
    public static Uni<List<RoleEntity>> getAllRoles() {
        return findAll().list();
    }

    /***
     * Get the last version of a role
     * @return Role entity
     */
    public static Uni<RoleEntity> getRoleLastVersion(String role) {
        return find("role = ?1 ORDER BY version DESC", role).firstResult();
    }

    /***
     * Get all versions of a role
     * @return Role entities
     */
    public static Uni<List<RoleEntity>> getRoleAllVersions(String role) {
        return list("role = ?1 ORDER BY version DESC", role);
    }

    /***
     * Group role records by role. There are multiple records for each role, one for each version.
     * @param roles The raw role records coming from the database
     * @return Map of roles, one set of records for each role
     */
    public static Map<String, List<RoleEntity>> groupRoles(List<RoleEntity> roles) {
        if(null == roles)
            return null;

        Map<String, List<RoleEntity>> map = new HashMap<>();

        for(var role : roles) {
            var versions = map.computeIfAbsent(role.role, k -> new ArrayList<RoleEntity>());

            versions.add(role);
        }

        for(var entry : map.entrySet()) {
            var versions = entry.getValue();
            if(versions.size() > 1) {
                versions.sort(new Comparator<RoleEntity>() {
                    @Override
                    public int compare(RoleEntity lhs, RoleEntity rhs) {
                        // -1 means lhs < rhs, 1 means lhs > rhs, 0 means equal for ascending sort
                        if(lhs.version < rhs.version)
                            return 1;
                        else if(lhs.version > rhs.version)
                            return -1;

                        return 0;
                    }
                });
            }
        }

        return map;
    }
}
