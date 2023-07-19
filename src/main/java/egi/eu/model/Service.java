package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/**
 * Details of a service (portfolio entry)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Service extends Version {

    @Schema(enumeration={ "Service" })
    public String kind = "Service";

    @Schema(description="ID of the service, assigned on creation")
    int id;

    String name;
    String description;

    // The fields below are linking this service to a portfolio entry
    // See process Service Portfolio Management (SPM)
    int portfolioId;
    int portfolioEntryId;
}
