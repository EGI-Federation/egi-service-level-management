package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


/**
 * Details of a service (portfolio entry)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Service extends Version {

    public String kind = "Service";

    int serviceId;

    String serviceName;
    String serviceDescription;

    // The fields below are linking this service to a portfolio entry
    // See process Service Portfolio Management (SPM)
    int portfolioId;
    int portfolioEntryId;
}
