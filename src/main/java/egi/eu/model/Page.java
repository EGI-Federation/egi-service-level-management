package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import jakarta.ws.rs.core.UriBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import egi.eu.Utils;


/**
 * Page of elements.
 *      T is the type of elements.
 *      M is the type of the offset/marker used in pagination.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Page<T, M> extends GenericEntity2<T, M> {

    @Schema(hidden = true)
    private URI baseUri;

    public M from;

    @Schema(hidden = true)
    public int limit;

    public long count;

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

        var fromType = getSecondTypeParameter();

        this.from = Utils.defaultValueFor(fromType);
        this.limit = 100;
        this.count = 0;
        this.elements = new ArrayList<>();
    }

    /**
     * Construct from source
     * @param baseUri The URI of the current page, or null to disable links to prev/next pages
     * @param from The number of elements to skip from the source
     * @param limit The maximum number of elements on the page
     * @param source The source of the elements to populate the page with
     */
    public Page(String baseUri, M from, int limit, List<T> source, boolean skipTo) {
        this();
        populate(baseUri, from, limit, source, skipTo);
    }

    /**
     * Populate with elements and setup pagination links.
     * @param baseUri The URI of the current page, or null to disable links to prev/next pages
     * @param from The number of elements to skip from the source
     * @param limit The maximum number of elements on the page
     * @param source The source of the elements to populate the page with
     * @param skipTo Whether to skip elements in the source
     * @return Ourselves to allow . chaining notation
     */
    public Page<T, M> populate(String baseUri, M from, int limit, List<T> source, boolean skipTo) {
        if(null == source)
            return this;

        var type = getSecondTypeParameter();
        boolean isLongFrom = Long.class.getTypeName().equals(type.getTypeName());

        // Populate page with elements
        this.from = from;
        this.limit = limit;

        if(skipTo && isLongFrom)
            this.elements = source.stream().skip((long)from).limit(limit).toList();
        else
            this.elements = source.stream().limit(limit).toList();

        this.count = this.elements.size();

        // Setup links to prev/next pages
        try {
            this.baseUri = null != baseUri ? new URI(baseUri) : null;
        } catch(URISyntaxException e) {
            // If the URI we got is invalid, we won't have links to prev/next pages
        }

        if(null != this.baseUri && isLongFrom) {
            final long longFrom = (long)from;
            long prevPageOffset = longFrom - limit;
            if(prevPageOffset < 0)
                prevPageOffset = 0;

            if(prevPageOffset < longFrom) {
                var prevUri = UriBuilder.fromUri(baseUri)
                                        .replaceQueryParam("from", prevPageOffset)
                                        .replaceQueryParam("limit", limit)
                                        .build();
                this.prevPage = prevUri.toString();
            }
            else
                this.prevPage = null;

            long nextPageOffset = longFrom + limit;
            if(nextPageOffset < source.size()) {
                var nextUri = UriBuilder.fromUri(baseUri)
                                        .replaceQueryParam("from", nextPageOffset)
                                        .replaceQueryParam("limit", limit)
                                        .build();
                this.nextPage = nextUri.toString();
            }
            else
                this.nextPage = null;
        }
        else {
            this.prevPage = null;
            this.nextPage = null;
        }

        return this;
    }

    /***
     * Set the link to the previous page
     * @param from The number of elements to skip from the source
     * @param limit The maximum number of elements on the page
     * @return Ourselves to allow . chaining notation
     */

    public Page<T, M> setPrevPage(M from, int limit) {

        if(null != this.baseUri) {
            var prevUri = UriBuilder.fromUri(baseUri)
                    .replaceQueryParam("from", from)
                    .replaceQueryParam("limit", limit)
                    .build();

            this.prevPage = prevUri.toString();
        }

        return this;
    }

    /***
     * Set the link to the next page
     * @param from The number of elements to skip from the source
     * @param limit The maximum number of elements on the page
     * @return Ourselves to allow . chaining notation
     */
    public Page<T, M> setNextPage(M from, int limit) {

        if(null != this.baseUri) {
            var nextUri = UriBuilder.fromUri(baseUri)
                                    .replaceQueryParam("from", from)
                                    .replaceQueryParam("limit", limit)
                                    .build();

            this.nextPage = nextUri.toString();
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
    public void clone(Page<T, M> page) {
        if(null != page) {
            this.elements = new ArrayList<T>();
            this.elements.addAll(page.elements);
            this.from = page.from;
            this.limit = page.limit;
            this.count = this.elements.size();
            this.prevPage = page.prevPage;
            this.nextPage =page.nextPage;
        }
        else {
            var fromType = getSecondTypeParameter();

            this.elements = new ArrayList<>();
            this.from = Utils.defaultValueFor(fromType);
            this.limit = 100;
            this.count = 0;
            this.prevPage = null;
            this.nextPage =null;
        }
    }

}
