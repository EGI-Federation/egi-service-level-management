package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

import egi.checkin.model.CheckinUser;


/***
 * Some process key performance indicator (KPI)
 */
public class KeyPerformanceIndicator extends VersionInfo {

    public enum KeyPerformanceIndicatorStatus {
        DRAFT(0),
        READY_FOR_APPROVAL(1),
        APPROVED(2),
        DEPRECATED(3);

        private final int value;
        private KeyPerformanceIndicatorStatus(int value) { this.value = value; }
        public int getValue() { return value; }
        public static KeyPerformanceIndicatorStatus of(int value) {
            return switch(value) {
                case 1 -> READY_FOR_APPROVAL;
                case 2 -> APPROVED;
                case 3 -> DEPRECATED;
                default -> DRAFT;
            };
        }
    }

    public enum KeyPerformanceIndicatorEscalation {
        NONE(0),
        PROCESS_MANAGER(1),
        IMS_MANAGER(2);

        private final int value;
        private KeyPerformanceIndicatorEscalation(int value) { this.value = value; }
        public int getValue() { return value; }
        public static KeyPerformanceIndicatorEscalation of(int value) {
            return switch(value) {
                case 1 -> PROCESS_MANAGER;
                case 2 -> IMS_MANAGER;
                default -> NONE;
            };
        }
    }

    @Schema(enumeration={ "KeyPerformanceIndicator" })
    public String kind = "KeyPerformanceIndicator";

    @Schema(description="ID of the KPI, assigned on creation")
    public Long id;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String code;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String description;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String criticalSuccessFactor;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String wayOfGathering;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int measurementFrequency;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(enumeration={ "sec", "min", "hour", "day", "month", "year" })
    public String frequencyUnit;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String target;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String whenToEscalate;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<Measurement> measurements;

    @Schema(description="Date and time of the next measurement. Always returned as UTC date and time.")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS", timezone = "UTC")
    public LocalDateTime nextMeasurement; // UTC

    @Schema(description="Updated on each measurement")
    public boolean onTrack;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public CheckinUser owner;

    public KeyPerformanceIndicatorStatus status;

    public KeyPerformanceIndicatorEscalation escalation;

    // Change history
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public HistoryOfKeyPerformanceIndicator history = null;


    /***
     * History of the KPI
     */
    public static class HistoryOfKeyPerformanceIndicator extends History<KeyPerformanceIndicator> {
        public HistoryOfKeyPerformanceIndicator() { super(); }
        public HistoryOfKeyPerformanceIndicator(List<KeyPerformanceIndicator> olderVersions) { super(olderVersions); }
    }


    /***
     * Measurement
     */
    public static class Measurement {

        @Schema(enumeration={ "Measurement" })
        public String kind = "Measurement";

        public Long id;

        public String value;
        public String target;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String escalateIf;

        @Schema(description="Date and time of the measurement. Always returned as UTC date and time.")
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS", timezone = "UTC")
        public LocalDateTime measuredOn; // UTC

        // Links to escalation Jira tickets
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public List<String> links  = null;
    }

}
