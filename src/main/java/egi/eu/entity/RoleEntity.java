package egi.eu.entity;

import org.hibernate.annotations.UpdateTimestamp;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import io.smallrye.common.constraint.NotNull;
import io.smallrye.mutiny.Uni;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import egi.eu.model.Role;


/**
 * Details of a role
 */
@Entity
@Table(name = "roles")
public class RoleEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(length = 20)
    @NotNull
    public String role;

    @Column(length = 50)
    @NotNull
    public String name; // Human-readable version of the role field

    @Column(length = 4096)
    public String tasks; // Markdown

    public Long globalRoleId;

    @Column(length = 50)
    public String globalRoleName;

    @Column(length = 4096)
    public String globalRoleTasks; // Markdown

    public int status;

    // Change tracking
    public int version = 1;

    @UpdateTimestamp
    public LocalDateTime changedOn;

    @Column(length = 1024)
    public String changeDescription;

    @ManyToOne(fetch = FetchType.EAGER,
            cascade = { CascadeType.PERSIST })
    @JoinTable(name = "role_editor",
            joinColumns = { @JoinColumn(name = "role_id") },
            inverseJoinColumns = { @JoinColumn(name = "user_id") })
    public UserEntity changeBy = null;


    /***
     * Constructor
     */
    public RoleEntity() { super(); }

    /***
     * Copy constructor
     */
    public RoleEntity(Role role) {
        super();

        this.role = role.role;
        this.name = role.name;
        this.tasks = role.tasks;
        this.globalRoleId = role.globalRoleId;
        this.globalRoleName = role.globalRoleName;
        this.globalRoleTasks = role.globalRoleTasks;
    }

    /***
     * Get all versions of all roles
     * @return All role entities, sorted in reverse chronological order (head of the list is the latest).
     */
    public static Uni<List<RoleEntity>> getAllRoles() {
        return findAll(Sort.by("changedOn").descending()).list();
    }

    /***
     * Get the last version of a role
     * @return Role entity
     */
    public static Uni<RoleEntity> getRoleLastVersion(String role) {
        return find("role", Sort.by("version").descending(), role).firstResult();
    }

    /***
     * Get all versions of a role
     * @return Role entities
     */
    public static Uni<List<RoleEntity>> getRoleAllVersions(String role) {
        return find("role", Sort.by("version").descending(), role).list();
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

        return map;
    }
}
