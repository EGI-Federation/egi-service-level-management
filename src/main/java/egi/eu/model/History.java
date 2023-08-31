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

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<T> versions;


    /**
     * Constructor
     */
    public History() {
        super("History", null, false);
    }

    /**
     * Construct from older versions
     */
    public History(List<T> versions) {
        this();
        populate(versions);
    }

    /***
     * Add versions
     * @param versions The entities to add.
     */
    public void populate(List<T> versions) {
        if(null == this.versions)
            this.versions = new ArrayList<>();

        this.versions.addAll(versions);
    }

    /***
     * Add a new version
     * @param version The entity to add.
     */
    public void add(T version) {
        if(null == this.versions)
            this.versions = new ArrayList<>();

        this.versions.add(version);
    }
}
