package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.ArrayList;


/**
 * Change history of an entity
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class History<T> {

    public String kind;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<Version<T>> versions;


    /**
     * Constructor
     */
    public History() {
        var type = getTypeParameter();
        if(null == type)
            this.kind = "History";
        else {
            var name = type.getTypeName();
            var index = name.lastIndexOf('.');
            name = index >= 0 ? name.substring(index + 1) : name;
            this.kind = String.format("HistoryOf%s", name);
        }

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

    /***
     * Helper to get the name of the type parameter.
     * @return Class of the type parameter, null on error
     */
    @SuppressWarnings("unchecked")
    private Class<T> getTypeParameter() {
        try {
            ParameterizedType superclass = (ParameterizedType) getClass().getGenericSuperclass();
            return (Class<T>) superclass.getActualTypeArguments()[0];
        }
        catch(Exception e) {
            return null;
        }
    }
}
