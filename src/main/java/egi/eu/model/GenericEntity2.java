package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.lang.reflect.ParameterizedType;


/**
 * Base generic entity with 2 type parameters
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class GenericEntity2<T, M> {

    public String kind;


    /**
     * Constructor
     * @param typeNamePrefix A prefix to use as part of the name
     * @param pluralName Whether to make the prefix plural (append 's')
     * @param typeNameSuffix A suffix to use as part of the name
     */
    protected GenericEntity2(String typeNamePrefix, String typeNameSuffix, boolean pluralName) {
        var type = getFirstTypeParameter();
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
     * Helper to get the name of the first type parameter (T).
     * @return Class of the type parameter, null on error
     */
    @SuppressWarnings("unchecked")
    protected Class<T> getFirstTypeParameter() {
        try {
            ParameterizedType superclass = (ParameterizedType) getClass().getGenericSuperclass();
            return (Class<T>) superclass.getActualTypeArguments()[0];
        }
        catch(Exception e) {
            return null;
        }
    }

    /***
     * Helper to get the name of the second type parameter (M).
     * @return Class of the type parameter, null on error
     */
    @SuppressWarnings("unchecked")
    protected Class<M> getSecondTypeParameter() {
        try {
            ParameterizedType superclass = (ParameterizedType) getClass().getGenericSuperclass();
            return (Class<M>) superclass.getActualTypeArguments()[1];
        }
        catch(Exception e) {
            return null;
        }
    }
}
