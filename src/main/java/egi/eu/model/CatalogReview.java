package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;


/**
 * Review of a catalog (of services)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CatalogReview extends Version {

    @Schema(enumeration={ "CatalogReview" })
    public String kind = "CatalogReview";

    int id;
    boolean foundInconsistencies;

    // Links
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<String> links  = null;

    // Change history
    @JsonInclude(JsonInclude.Include.NON_NULL)
    HistoryOfCatalogReview history = null;

    /***
     * History of a catalog review
     */
    public class HistoryOfCatalogReview extends History<CatalogReview> {
        public HistoryOfCatalogReview() { super(); }
    }
}
