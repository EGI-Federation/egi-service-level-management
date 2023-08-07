package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import egi.checkin.model.UserInfo;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;


/***
 * Process input or output
 */
public class Interface {

    @Schema(enumeration={ "Interface" })
    public String kind = "Interface";

    @Schema(description="ID of the process interface, assigned on creation")
    long id;

    @Schema(enumeration={ "In", "Out" })
    public String direction;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String description;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String relevantMaterial;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(enumeration={ "Internal", "External", "Customer",
            "BA", "BDS", "CAPM", "CHM", "COM", "CONFM", "CSI", "CRM", "CPM", "FA", "HR", "ISM",
            "ISRM", "PPM", "PM", "PKM", "PPM", "RDM", "RM", "SACM", "SRM", "SLM", "SPM", "SRM" })
    public String interfacesWith;

    // Change history
    @JsonInclude(JsonInclude.Include.NON_NULL)
    HistoryOfInterface history = null;


    /***
     * History of an input/output
     */
    public static class HistoryOfInterface extends History<Interface> {
        public HistoryOfInterface() { super(); }
    }

}
