package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

import egi.checkin.model.CheckinUser;


/**
 * Details of a catalog (of services)
 * Catalogs are a subset of a service portfolio, including only those services
 * that are available to users/clients.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Catalog extends VersionInfo {

    @Schema(enumeration={ "Catalog" })
    public String kind = "Catalog";

    @Schema(description="ID of the catalog, assigned on creation")
    public Long id;

    public String name;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String description;

    @Schema(description="The services that are included in the catalog. " +
            "All services must be from the same portfolio as the catalog.")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<Service> services;

    // The fields below are linking this catalog to a service portfolio
    // See process Service Portfolio Management (SPM)
    public Long spmPortfolioId;
    public String spmPortfolioName;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public CheckinUser owner;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int reviewFrequency;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(enumeration={ "sec", "min", "hour", "day", "month", "year" })
    public String frequencyUnit;

    @Schema(description="Date and time of the next review. Always returned as UTC date and time.")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonSerialize(using = UtcLocalDateTimeSerializer.class)
    public LocalDateTime nextReview; // UTC

    // Links
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<String> links  = null;

    // Change history
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public HistoryOfCatalog history = null;


    /***
     * History of a catalog
     */
    public static class HistoryOfCatalog extends History<Catalog> {
        public HistoryOfCatalog() { super(); }
        public HistoryOfCatalog(List<Catalog> olderVersions) { super(olderVersions); }
    }
}
