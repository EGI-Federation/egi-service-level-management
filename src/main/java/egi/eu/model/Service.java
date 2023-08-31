package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;


/**
 * Details of a service (portfolio entry)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Service extends VersionInfo {

    @Schema(enumeration={ "Service" })
    public String kind = "Service";

    @Schema(description="ID of the service, assigned on creation")
    public Long id;

    public String name;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String description;

    // The fields below are linking this service to a portfolio entry
    // See process Service Portfolio Management (SPM)
    public Long spmPortfolioId;
    public Long spmPortfolioEntryId;

    // Links
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<String> links  = null;

    // Change history
    @JsonInclude(JsonInclude.Include.NON_NULL)
    HistoryOfService history = null;


    /***
     * History of a service
     */
    public static class HistoryOfService extends History<Service> {
        public HistoryOfService() { super(); }
        public HistoryOfService(List<Service> olderVersions) { super(olderVersions); }
    }
}
