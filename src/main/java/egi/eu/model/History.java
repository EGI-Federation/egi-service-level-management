package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.ArrayList;


/**
 * Change history of an entity
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class History<T> extends GenericEntity<T> {

    public String kind;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<Version<T>> versions;


    /**
     * Constructor
     */
    public History() {
        super("History", null, false);
        this.versions = new ArrayList<>();
    }

    /***
     * Add a new version
     * @param version The entity to add.
     */
    public void add(Version<T> version) {
        if(null == this.versions)
            this.versions = new ArrayList<>();

        this.versions.add(version);
    }
}
