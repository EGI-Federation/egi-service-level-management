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

    public String kind = "Catalog";

    int catalogId;

    String catalogName;

    // The fields below are linking this catalog to a service portfolio
    // See process Service Portfolio Management (SPM)
    int portfolioId;
    String portfolioName;

    @Schema(description="The services that are included in the catalog. " +
                        "All services must be from the same portfolio as the catalog.")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<Service> services;
}
