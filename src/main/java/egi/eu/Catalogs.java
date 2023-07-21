package egi.eu;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import io.quarkus.security.identity.SecurityIdentity;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.RestHeader;
import io.smallrye.mutiny.Uni;
import io.micrometer.core.instrument.MeterRegistry;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import egi.checkin.model.UserInfo;
import egi.eu.model.*;


/***
 * Resource for Underpinning Agreement (UA) queries and operations.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class Catalogs extends BaseResource {

    private static final Logger log = Logger.getLogger(Catalogs.class);

    @Inject
    MeterRegistry registry;

    @Inject
    SecurityIdentity identity;

    /***
     * Page of catalogs
     */
    class PageOfCatalogs extends Page<Catalog> {
        public PageOfCatalogs(int first) { super(first); }
        public PageOfCatalogs(int first, int size) { super(first, size); }
    }


    /***
     * Construct with meter
     */
    public Catalogs() { super(log); }

    /**
     * List all catalogs.
     * @param auth The access token needed to call the service.
     * @param firstItem The first item to return (0-based).
     * @param pageSize The maximum number of items to return.
     * @param allVersions True to return all versions of the items.
     * @return API Response, wraps an ActionSuccess(Page<{@link Catalog>) or an ActionError entity
     */
    @GET
    @Path("/catalog/list")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.ISM_USER)
    @Operation(operationId = "listCatalogs",  summary = "List all catalogs")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PageOfCatalogs.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "404", description="No items found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> listCatalogs(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                      @RestQuery("from") @DefaultValue("0")
                                      @Parameter(required = false,
                                                 description = "The first item to return")
                                      int firstItem,
                                      @RestQuery("count") @DefaultValue("100")
                                      @Parameter(required = false,
                                                 description = "The maximum number of items to return")
                                      int pageSize,
                                      @RestQuery("allVersions") @DefaultValue("false")
                                      @Parameter(required = false, description = "Whether to retrieve all versions")
                                      boolean allVersions)
    {
        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("firstItem", firstItem);
        addToDC("pageSize", pageSize);
        addToDC("allVersions", allVersions);

        log.info("Listing catalogs");

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Got UA list, success
                log.info("Got catalog list");
                var list = new PageOfCatalogs(0);
                return Uni.createFrom().item(Response.ok(list).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to list catalogs");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Create new catalog.
     * @param auth The access token needed to call the service.
     * @param catalog The new catalog.
     * @return API Response, wraps an ActionSuccess({@link Catalog}) or an ActionError entity
     */
    @POST
    @Path("/catalog")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.CATALOG_MANAGER })
    @Operation(operationId = "createCatalog",  summary = "Create new catalog")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = Catalog.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> createCatalog(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                       Catalog catalog)
    {
        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("catalog", catalog);

        log.info("Creating catalog");

        Uni<Response> result = Uni.createFrom().item(new Catalog())

            .chain(created -> {
                // Create complete, success
                log.info("Created catalog");
                return Uni.createFrom().item(Response.ok(created).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to create catalog");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Get existing catalog.
     * @param auth The access token needed to call the service.
     * @param catalogId The ID of the catalog to fetch.
     * @param allVersions True to return all versions of the items.
     * @return API Response, wraps an ActionSuccess({@link Catalog}) or an ActionError entity
     */
    @GET
    @Path("/catalog/{catalogId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.ISM_USER)
    @Operation(operationId = "getCatalog",  summary = "Get existing catalog")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = Catalog.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "404", description="Not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> fetchCatalog(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                      @RestPath("catalogId")
                                      @Parameter(required = true, description = "ID of catalog to get")
                                      int catalogId,
                                      @RestQuery("allVersions") @DefaultValue("false")
                                      @Parameter(required = false, description = "Whether to retrieve all versions")
                                      boolean allVersions)
    {
        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("catalogID", catalogId);
        addToDC("allVersions", allVersions);

        log.info("Getting catalog");

        Uni<Response> result = Uni.createFrom().item(new Catalog())

            .chain(catalog -> {
                // Got catalog, success
                log.info("Got catalog");
                return Uni.createFrom().item(Response.ok(catalog).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to get catalog");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Add service to existing catalog.
     * @param auth The access token needed to call the service.
     * @param catalogId The ID of the catalog where to include the service.
     * @param service The service to add to the catalog.
     * @return API Response, wraps an ActionSuccess({@link Service}) or an ActionError entity
     */
    @POST
    @Path("/catalog/{catalogId}/service")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.CATALOG_MANAGER })
    @Operation(operationId = "addService",  summary = "Add service to existing catalog")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Added",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = Service.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "404", description="Not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> addService(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                   @RestPath("catalogId")
                                   @Parameter(required = true, description = "ID of catalog where to add the service")
                                   int catalogId,
                                   Service service)
    {
        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("catalogId", catalogId);
        addToDC("service", service);

        log.info("Adding service to catalog");

        Uni<Response> result = Uni.createFrom().item(new Service())

                .chain(added -> {
                    // Add complete, success
                    log.info("Added service to catalog");
                    return Uni.createFrom().item(Response.ok(added).build());
                })
                .onFailure().recoverWithItem(e -> {
                    log.error("Failed to add service to catalog");
                    return new ActionError(e).toResponse();
                });

        return result;
    }

    /**
     * Get service in existing catalog.
     * @param auth The access token needed to call the service.
     * @param catalogId The ID of the catalog that contains the service.
     * @param serviceId The ID of the service to fetch.
     * @param allVersions True to return all versions of the items.
     * @return API Response, wraps an ActionSuccess({@link Service}) or an ActionError entity
     */
    @GET
    @Path("/catalog/{catalogId}/service/{serviceId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.ISM_USER)
    @Operation(operationId = "getService",  summary = "Get service in existing catalog")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = Service.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "404", description="Not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> fetchService(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                      @RestPath("catalogId")
                                      @Parameter(required = true, description = "ID of catalog that contains the service")
                                      int catalogId,
                                      @RestPath("serviceId")
                                      @Parameter(required = true, description = "ID of service to get")
                                      int serviceId,
                                      @RestQuery("allVersions") @DefaultValue("false")
                                      @Parameter(required = false, description = "Whether to retrieve all versions")
                                      boolean allVersions)
    {
        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("catalogID", catalogId);
        addToDC("serviceID", serviceId);
        addToDC("allVersions", allVersions);

        log.info("Getting service");

        Uni<Response> result = Uni.createFrom().item(new Service())

                .chain(catalog -> {
                    // Got service, success
                    log.info("Got service");
                    return Uni.createFrom().item(Response.ok(catalog).build());
                })
                .onFailure().recoverWithItem(e -> {
                    log.error("Failed to get service");
                    return new ActionError(e).toResponse();
                });

        return result;
    }

    /**
     * Update service in existing catalog.
     * @param auth The access token needed to call the service.
     * @param catalogId The ID of the catalog that contains the service.
     * @param serviceId The ID of the service to update.
     * @return API Response, wraps an ActionSuccess({@link Service}) or an ActionError entity
     */
    @PUT
    @Path("/catalog/{catalogId}/service/{serviceId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.CATALOG_MANAGER })
    @Operation(operationId = "updateService",  summary = "Update service in existing catalog")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = Service.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "404", description="Not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> updateService(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                       @RestPath("catalogId")
                                       @Parameter(required = true, description = "ID of catalog that contains the service")
                                       int catalogId,
                                       @RestPath("serviceId")
                                       @Parameter(required = true, description = "ID of service to update")
                                       int serviceId,
                                       Service service)
    {
        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("catalogId", catalogId);
        addToDC("serviceId", serviceId);
        addToDC("service", service);

        log.info("Updating service in catalog");

        Uni<Response> result = Uni.createFrom().item(new Service())

                .chain(updated -> {
                    // Update complete, success
                    log.info("Updated service in catalog");
                    return Uni.createFrom().item(Response.ok(updated).build());
                })
                .onFailure().recoverWithItem(e -> {
                    log.error("Failed to update service in catalog");
                    return new ActionError(e).toResponse();
                });

        return result;
    }

    /**
     * Remove service from existing catalog.
     * @param auth The access token needed to call the service.
     * @param catalogId The ID of the catalog that contains the service.
     * @param serviceId The ID of the service to remove.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @DELETE
    @Path("/catalog/{catalogId}/service/{serviceId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.CATALOG_MANAGER })
    @Operation(operationId = "removeService",  summary = "Remove service from existing catalog")
    @APIResponses(value = {
            @APIResponse(responseCode = "204", description = "Removed"),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "404", description="Not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> removeService(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                       @RestPath("catalogId")
                                       @Parameter(required = true, description = "ID of catalog that contains the service")
                                       int catalogId,
                                       @RestPath("serviceId")
                                       @Parameter(required = true, description = "ID of service to remove")
                                       int serviceId)
    {
        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("catalogId", catalogId);
        addToDC("serviceId", serviceId);

        log.info("Removing service from catalog");

        Uni<Response> result = Uni.createFrom().item(new Service())

                .chain(updated -> {
                    // Removal complete, success
                    log.info("Removed service from catalog");
                    return Uni.createFrom().item(Response.ok(updated).build());
                })
                .onFailure().recoverWithItem(e -> {
                    log.error("Failed to remove service from catalog");
                    return new ActionError(e).toResponse();
                });

        return result;
    }

    /**
     * Review existing catalog.
     * @param auth The access token needed to call the service.
     * @param catalogId The ID of the catalog to review.
     * @param review The details of the catalog review.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @POST
    @Path("/catalog/{catalogId}/review")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.CATALOG_MANAGER })
    @Operation(operationId = "reviewCatalog",  summary = "Review existing catalog")
    @APIResponses(value = {
            @APIResponse(responseCode = "204", description = "Signed"),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "404", description="Not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> reviewCatalog(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                       @RestPath("catalogId")
                                       @Parameter(required = true, description = "ID of catalog to review")
                                       int catalogId,
                                       CatalogReview review)
    {
        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("catalogID", catalogId);

        log.info("Reviewing catalog");

        Uni<Response> result = Uni.createFrom().item(new Catalog())

            .chain(signed -> {
                // Review complete, success
                log.info("Reviewed catalog");
                return Uni.createFrom().item(Response.ok().status(Status.NO_CONTENT).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to review catalog");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Retire existing catalog.
     * @param auth The access token needed to call the service.
     * @param catalogId The ID of the catalog to retire.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @DELETE
    @Path("/catalog/{catalogId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.CATALOG_MANAGER })
    @Operation(operationId = "retireCatalog",  summary = "Retire existing catalog")
    @APIResponses(value = {
            @APIResponse(responseCode = "204", description = "Retired"),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "404", description="Not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> retireCatalog(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                       @RestPath("catalogId")
                                       @Parameter(required = true, description = "ID of catalog to retire")
                                       int catalogId)
    {
        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("catalogID", catalogId);

        log.info("Retiring catalog");

        Uni<Response> result = Uni.createFrom().item(new Catalog())

            .chain(revoked -> {
                // Retire complete, success
                log.info("Retired catalog");
                return Uni.createFrom().item(Response.ok().status(Status.NO_CONTENT).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to retire catalog");
                return new ActionError(e).toResponse();
            });

        return result;
    }

}