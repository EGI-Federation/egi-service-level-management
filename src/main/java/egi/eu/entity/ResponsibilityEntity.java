package egi.eu.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.smallrye.mutiny.Uni;
import jakarta.persistence.*;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import egi.eu.model.Responsibility;
import egi.eu.model.Responsibility.ResponsibilityStatus;


/**
 * The description of the responsibilities in the process
 */
@Entity
@Table(name = "responsibility")
public class ResponsibilityEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(length = 10240)
    public String description;

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

    @Column(length = 2048)
    public String changeDescription;

    @ManyToOne(fetch = FetchType.EAGER,
            cascade = { CascadeType.PERSIST })
    @JoinTable(name = "responsibility_editor_map",
            joinColumns = { @JoinColumn(name = "responsibility_id") },
            inverseJoinColumns = { @JoinColumn(name = "user_id") })
    public UserEntity changeBy = null;


    /***
     * Constructor
     */
    public ResponsibilityEntity() { super(); }


    /***
     * Copy constructor with new status
     * @param resp The responsibilities to copy
     * @param newStatus The new status
     */
    public ResponsibilityEntity(ResponsibilityEntity resp, ResponsibilityStatus newStatus) {
        super();

        // Copy simple fields
        this.description = resp.description;
        this.reviewFrequency = resp.reviewFrequency;
        this.frequencyUnit = resp.frequencyUnit;
        this.nextReview = resp.nextReview;
        this.status = newStatus.getValue();
    }

    /***
     * Copy constructor
     * @param resp The new version (from the frontend)
     * @param latest The latest version in the database
     * @param users The users that already exist in the database
     */
    public ResponsibilityEntity(Responsibility resp, ResponsibilityEntity latest, Map<String, UserEntity> users) {
        super();

        this.changeDescription = resp.changeDescription;
        this.version = null == latest ? 1 : latest.version + 1;
        if(null != resp.changeBy) {
            if(null != users && users.containsKey(resp.changeBy.checkinUserId))
                this.changeBy = users.get(resp.changeBy.checkinUserId);
            else
                this.changeBy = new UserEntity(resp.changeBy);
        }

        this.description = resp.description;
        this.reviewFrequency = resp.reviewFrequency;
        this.frequencyUnit = resp.frequencyUnit;
        this.nextReview = resp.nextReview;

        final var latestStatus = ResponsibilityStatus.of(latest.status);
        if(ResponsibilityStatus.APPROVED == latestStatus)
            // Changing an approved entity will require a new approval
            this.status = ResponsibilityStatus.DRAFT.getValue();
        else
            this.status = latestStatus.getValue();
    }

    /***
     * Get the latest version as a list with one element
     * @return List with latest version of the entity
     */
    public static Uni<List<ResponsibilityEntity>> getLastVersionAsList() {
        return find("ORDER BY version DESC").range(0,0).list();
    }

    /***
     * Get the latest version
     * @return Latest version of the entity
     */
    public static Uni<ResponsibilityEntity> getLastVersion() {
        return find("ORDER BY version DESC").firstResult();
    }

    /***
     * Get all versions
     * @return All versions of the entity, sorted in reverse chronological order (head of the list is the latest).
     */
    public static Uni<List<ResponsibilityEntity>> getAllVersions() {
        return find("ORDER BY version DESC").list();
    }

    /***
     * Get all versions, paged
     * @return All versions of the entity, sorted in reverse chronological order (head of the list is the latest).
     */
    public static Uni<List<ResponsibilityEntity>> getAllVersions(int index, int size) {
        return find("ORDER BY version DESC").page(index, size).list();
    }
}
