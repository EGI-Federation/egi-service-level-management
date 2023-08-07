package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import egi.checkin.model.UserInfo;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;


/***
 * Some process requirement
 */
public class Requirement {

    @Schema(enumeration={ "Requirement" })
    public String kind = "Requirement";

    @Schema(description="ID of the requirement, assigned on creation")
    long id;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String code;

    public String requirement;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String source;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<UserInfo> responsibles;

    // Change history
    @JsonInclude(JsonInclude.Include.NON_NULL)
    HistoryOfRequirement history = null;


    /***
     * History of the requirement
     */
    public static class HistoryOfRequirement extends History<Requirement> {
        public HistoryOfRequirement() { super(); }
    }

}
