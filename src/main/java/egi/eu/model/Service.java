package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;


/**
 * Details of a service (portfolio entry)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Service extends Version {

    @Schema(enumeration={ "Service" })
    public String kind = "Service";

    @Schema(description="ID of the service, assigned on creation")
    long id;

    String name;
    String description;

    // The fields below are linking this service to a portfolio entry
    // See process Service Portfolio Management (SPM)
    long spmPortfolioId;
    long spmPortfolioEntryId;

    // Links
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<String> links  = null;

    // Change history
    @JsonInclude(JsonInclude.Include.NON_NULL)
    HistoryOfService history = null;


    /***
     * History of a service
     */
    public class HistoryOfService extends History<Service> {
        public HistoryOfService() { super(); }
    }
}
