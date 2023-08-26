package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import egi.checkin.model.CheckinUser;
import jakarta.persistence.Column;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Date;
import java.util.List;


/***
 * Procedure in this IMS process
 */
public class Procedure {

    public enum ProcedureStatus {
        DRAFT,
        READY_FOR_APPROVAL,
        APPROVED,
        IMPLEMENTED,
        DEPRECATED
    }

    @Schema(enumeration={ "Procedure" })
    public String kind = "Procedure";

    @Schema(description="ID of the procedure, assigned on creation")
    public long id;

    public String code;
    public String title;
    public String process = Process.PROCESS_CODE;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String overview;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String definitions;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<Trigger> triggers;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<Step> steps;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int reviewFrequency;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(enumeration={ "sec", "min", "hour", "day", "month", "year" })
    public String frequencyUnit;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date nextReview;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public CheckinUser owner;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public CheckinUser approver;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date approvedOn;

    public ProcedureStatus status;

    // Links
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<String> links  = null;

    // Change history
    @JsonInclude(JsonInclude.Include.NON_NULL)
    HistoryOfProcedure history = null;


    /***
     * History of the KPI
     */
    public static class HistoryOfProcedure extends History<Procedure> {
        public HistoryOfProcedure() { super(); }
    }


    /***
     * Trigger of a procedure
     */
    public static class Trigger {

        @Schema(enumeration={ "Trigger" })
        public String kind = "Trigger";

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @Column(length = 2048)
        public String description;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public Role role;

        // The fields below are linking this trigger to another procedure
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @Column(length = 10)
        public String procedureProcess;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String procedureCode;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String procedureName;
    }

    /***
     * Step in a procedure
     */
    public static class Step {
        @Schema(enumeration = { "Step" })
        public String kind = "Step";

        public Long id;

        // The responsible person(s) must have this role
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public List<Role> responsible;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String action;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String prerequisites;
    }
}
