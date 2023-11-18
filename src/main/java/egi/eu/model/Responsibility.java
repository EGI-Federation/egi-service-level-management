package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import egi.eu.entity.ResponsibilityEntity;


/***
 * The description of the responsibilities in the process
 */
public class Responsibility extends VersionInfo {

    public enum ResponsibilityStatus {
        DRAFT(0),
        READY_FOR_APPROVAL(1),
        APPROVED(2),
        DEPRECATED(3);

        private final int value;
        private ResponsibilityStatus(int value) { this.value = value; }
        public int getValue() { return value; }
        public static ResponsibilityStatus of(int value) {
            return switch(value) {
                case 1 -> READY_FOR_APPROVAL;
                case 2 -> APPROVED;
                case 3 -> DEPRECATED;
                default -> DRAFT;
            };
        }
    }

    @Schema(enumeration={ "Responsibilities" })
    public String kind = "Responsibilities";

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Long id;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String description; // Markdown

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int reviewFrequency;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(enumeration={ "day", "month", "year" })
    public String frequencyUnit;

    @Schema(description="Date and time of the next review. Always returned as UTC date and time.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS", timezone = "UTC")
    public LocalDateTime nextReview; // UTC

    public ResponsibilityStatus status = ResponsibilityStatus.DRAFT;

    // Change history
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public HistoryOfResponsibility history = null;


    /***
     * History of the responsibilities
     */
    public static class HistoryOfResponsibility extends History<Responsibility> {
        public HistoryOfResponsibility() { super(); }
        public HistoryOfResponsibility(List<Responsibility> olderVersions) { super(olderVersions); }
    }

    /***
     * Constructor
     */
    public Responsibility() {}

    /***
     * Copy constructor
     * @param resp The entity to copy
     */
    public Responsibility(ResponsibilityEntity resp) {

        if(null == resp)
            return;

        this.id = resp.id;
        this.description = resp.description;
        this.status = ResponsibilityStatus.of(resp.status);
        this.reviewFrequency = resp.reviewFrequency;
        this.frequencyUnit = resp.frequencyUnit;
        this.nextReview = (null == resp.nextReview) ? null :
                resp.nextReview
                    .atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();

        this.version = resp.version;
        this.changeDescription = resp.changeDescription;
        if(null != resp.changeBy)
            this.changeBy = new User(resp.changeBy);
        this.changedOn = (null == resp.changedOn) ? null :
                resp.changedOn
                    .atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
    }

    /***
     * Construct from history.
     * @param respVersions The list of versions, should start with the latest version.
     */
    public Responsibility(List<ResponsibilityEntity> respVersions) {
        // Head of the list as the current version
        this(respVersions.get(0));

        // The rest of the list as the history of this entity
        var olderVersions = respVersions.stream().skip(1).map(entity -> {
                return new Responsibility(entity);
            }).toList();

        if(!olderVersions.isEmpty())
            this.history = new HistoryOfResponsibility(olderVersions);
    }
}
