package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/**
 * Page of elements
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Page<T> extends GenericEntity<T> {

    @Schema(hidden = true)
    private URI baseUri;

    public long offset;
    public long limit;
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
    public Page() {
        super("Page", null, true);

        this.offset = 0;
        this.limit = 100;
        this.count = 0;
        this.elements = new ArrayList<>();
    }

    /**
     * Construct from source
     * @param baseUri The URI of the current page, or null to disable links to prev/next pages
     * @param offset The number of elements to skip from the source
     * @param limit The maximum number of elements on the page
     * @param source The source of the elements to populate the page with
     */
    public Page(String baseUri, long offset, long limit, List<T> source) {
        this();
        populate(baseUri, offset, limit, source);
    }

    /**
     * Populate with elements and setup pagination links.
     * @param baseUri The URI of the current page, or null to disable links to prev/next pages
     * @param offset The number of elements to skip from the source
     * @param limit The maximum number of elements on the page
     * @param source The source of the elements to populate the page with
     */
    public Page<T> populate(String baseUri, long offset, long limit, List<T> source) {
        if(null == source)
            return this;

        // Populate page with elements
        this.offset = offset;
        this.limit = limit;
        this.elements = source.stream().skip(offset).limit(limit).toList();
        this.count = this.elements.size();

        // Setup links to prev/next pages
        try {
            this.baseUri = null != baseUri ? new URI(baseUri) : null;
        } catch (URISyntaxException e) {
            // If the URI we got is invalid, we won't have links to prev/next pages
        }

        if(null != this.baseUri) {
            long prevPageOffset = offset - limit;
            if(prevPageOffset < 0)
                prevPageOffset = 0;

            if(prevPageOffset < offset) {
                var prevUri = UriBuilder.fromUri(baseUri).replaceQueryParam("offset", prevPageOffset).build();
                this.prevPage = prevUri.toString();
            }
            else
                this.prevPage = null;

            long nextPageOffset = offset + limit;
            if(nextPageOffset < source.size()) {
                var nextUri = UriBuilder.fromUri(baseUri).replaceQueryParam("offset", nextPageOffset).build();
                this.nextPage = nextUri.toString();
            }
            else
                this.nextPage = null;
        }

        return this;
    }

    /***
     * Add a new element to the page.
     * @param element The element to add
     */
    public void add(T element) {
        if(null == this.elements) {
            this.elements = new ArrayList<>();
            this.count = 0;
        }

        this.elements.add(element);
        this.count++;
    }

    /***
     * Add all elements from another page.
     * @param page Page to clone
     */
    public void clone(Page<T> page) {
        if(null != page) {
            this.elements = new ArrayList<>(page.count);
            this.elements.addAll(page.elements);
            this.offset = page.offset;
            this.limit = page.limit;
            this.count = this.elements.size();
            this.prevPage = page.prevPage;
            this.nextPage =page.nextPage;
        }
        else {
            this.elements = new ArrayList<>();
            this.offset = 0;
            this.limit = 100;
            this.count = 0;
            this.prevPage = null;
            this.nextPage =null;
        }
    }
}
