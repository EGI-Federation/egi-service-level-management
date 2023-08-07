package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;


/**
 * Base generic entity
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class GenericEntity<T> {

    public String kind;


    /**
     * Constructor
     * @param typeNamePrefix A prefix to use as part of the name
     * @param pluralName Whether to make the prefix plural (append 's')
     * @param typeNameSuffix A suffix to use as part of the name
     */
    protected GenericEntity(String typeNamePrefix, String typeNameSuffix, boolean pluralName) {
        var type = getTypeParameter();
        if(null != type) {
            var name = type.getTypeName();
            var index = name.lastIndexOf('.');
            name = index >= 0 ? name.substring(index + 1) : name;

            if(null != typeNamePrefix)
                this.kind = String.format("%sOf%s%s", typeNamePrefix, name, pluralName ? "s" : "");
            else if(null != typeNameSuffix)
                this.kind = name + typeNameSuffix;
        }

        if(null == kind || kind.isBlank()) {
            if(null != typeNamePrefix)
                this.kind = typeNamePrefix;
            else
                this.kind = typeNameSuffix;
        }
    }

    /***
     * Helper to get the name of the type parameter.
     * @return Class of the type parameter, null on error
     */
    @SuppressWarnings("unchecked")
    protected Class<T> getTypeParameter() {
        try {
            ParameterizedType superclass = (ParameterizedType) getClass().getGenericSuperclass();
            return (Class<T>) superclass.getActualTypeArguments()[0];
        }
        catch(Exception e) {
            return null;
        }
    }
}
