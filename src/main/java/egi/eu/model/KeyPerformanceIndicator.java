package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import egi.checkin.model.UserInfo;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Date;
import java.util.List;


/***
 * Some process key performance indicator (KPI)
 */
public class KeyPerformanceIndicator {

    public enum Status {
        DRAFT,
        READY_FOR_APPROVAL,
        APPROVED,
        DEPRECATED
    }

    public enum Escalation {
        NONE,
        PROCESS_OWNER,
        IMS_MANAGER
    }

    @Schema(enumeration={ "KeyPerformanceIndicator" })
    public String kind = "KeyPerformanceIndicator";

    @Schema(description="ID of the KPI, assigned on creation")
    public long id;

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

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date nextMeasurement;

    @Schema(description="Updated on each measurement")
    public boolean onTrack;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public UserInfo owner;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public UserInfo approver;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date approvedOn;

    public Status status;

    public Escalation escalation;

    // Change history
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public HistoryOfKeyPerformanceIndicator history = null;


    /***
     * History of the KPI
     */
    public static class HistoryOfKeyPerformanceIndicator extends History<KeyPerformanceIndicator> {
        public HistoryOfKeyPerformanceIndicator() { super(); }
    }


    /***
     * Measurement
     */
    public static class Measurement {

        @Schema(enumeration={ "Measurement" })
        public String kind = "Measurement";

        public long id;

        public String value;
        public String target;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String escalateIf;

        public Date measuredOn;

        // Links to escalation Jira tickets
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public List<String> links  = null;
    }

}
