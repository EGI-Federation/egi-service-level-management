package egi.eu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;


/**
 * Page of elements
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Page<T> {

    public String kind;
    public int first;
    public int count;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<T> elements;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String prevPage;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String nextPage;


    /**
     * Constructor
     */
    public Page(int first) {
        var type = new GenericClass<T>() {};
        this.kind = "PageOf" + type.getType().getTypeName();
        this.first = first;
        this.count = 0;
        this.elements = new ArrayList<>();
    }

    /**
     * Constructor with allocation
     * @param size Initial capacity for storage elements.
     */
    public Page(int first, int size) {
        var type = new GenericClass<T>() {};
        this.kind = "PageOf" + type.getType().getTypeName();
        this.first = first;
        this.count = 0;
        this.elements = new ArrayList<>(size);
    }

    /***
     * Add a new element
     * @param element The element to add.
     */
    public void add(T element) {
        if(null != element) {
            this.elements.add(element);
            this.count++;
        }
    }

    /***
     * Add all elements from another page
     * @param page Page to add all elements from.
     */
    public void clone(Page<T> page) {
        if(null != page) {
            this.elements = new ArrayList<>(page.count);
            this.elements.addAll(page.elements);
            this.first = page.first;
            this.count = this.elements.size();
        }
        else {
            this.elements = new ArrayList<>();
            this.first = first;
            this.count = 0;
        }
    }
}
