package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;


/**
 * Details of a catalog (of services)
 * Catalogs are a subset of a service portfolio, including only those services
 * that are available to users/clients.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Catalog extends Version {

    @Schema(enumeration={ "Catalog" })
    public String kind = "Catalog";

    @Schema(description="ID of the catalog, assigned on creation")
    long id;

    String name;

    @Schema(description="The services that are included in the catalog. " +
            "All services must be from the same portfolio as the catalog.")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<Service> services;

    // The fields below are linking this catalog to a service portfolio
    // See process Service Portfolio Management (SPM)
    long spmPortfolioId;
    String spmPortfolioName;

    // Links
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<String> links  = null;

    // Change history
    @JsonInclude(JsonInclude.Include.NON_NULL)
    HistoryOfCatalog history = null;


    /***
     * History of a catalog
     */
    public class HistoryOfCatalog extends History<Catalog> {
        public HistoryOfCatalog() { super(); }
    }
}
