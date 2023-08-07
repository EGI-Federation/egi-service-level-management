package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import egi.checkin.model.UserInfo;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Date;
import java.util.List;


/**
 * Details of a catalog (of services)
 * Catalogs are a subset of a service portfolio, including only those services
 * that are available to users/clients.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Catalog extends Version<Catalog> {

    @Schema(enumeration={ "Catalog" })
    public String kind = "Catalog";

    @Schema(description="ID of the catalog, assigned on creation")
    public long id;

    String name;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String description;

    @Schema(description="The services that are included in the catalog. " +
            "All services must be from the same portfolio as the catalog.")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<Service> services;

    // The fields below are linking this catalog to a service portfolio
    // See process Service Portfolio Management (SPM)
    public long spmPortfolioId;
    public String spmPortfolioName;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    UserInfo owner;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int reviewFrequency;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(enumeration={ "sec", "min", "hour", "day", "month", "year" })
    public String frequencyUnit;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date nextReview;

    // Links
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<String> links  = null;

    // Change history
    @JsonInclude(JsonInclude.Include.NON_NULL)
    HistoryOfCatalog history = null;


    /***
     * History of a catalog
     */
    public static class HistoryOfCatalog extends History<Catalog> {
        public HistoryOfCatalog() { super(); }
    }
}
