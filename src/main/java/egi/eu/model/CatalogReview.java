package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;


/**
 * Review of a catalog (of services)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CatalogReview extends Version {

    public String kind = "CatalogReview";

    int one;
    int two;
    boolean test;
}
