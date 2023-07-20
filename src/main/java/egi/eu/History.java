package egi.eu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.ArrayList;


/**
 * Change history of an entity
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class History<T> {

    public String kind;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<T> versions;


    /**
     * Constructor
     */
    public History() {
        var type = new GenericClass<T>() {};
        this.kind = "HistoryOf" + type.getType().getTypeName();
        this.versions = new ArrayList<>();
    }

    /**
     * Constructor with allocation
     * @param size Initial capacity for versions.
     */
    public History(int size) {
        var type = new GenericClass<T>() {};
        this.kind = "HistoryOf" + type.getType().getTypeName();
        this.versions = new ArrayList<>(size);
    }

    /***
     * Add a new version
     * @param version The entity to add.
     */
    public void add(T version) {
        var type = new GenericClass<T>() {};
        this.kind = "HistoryOf" + type.getType().getTypeName();
        if(null != version) {
            this.versions.add(version);
        }
    }
}
