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
public class UnderpinningAgreements extends BaseResource {

    private static final Logger log = Logger.getLogger(UnderpinningAgreements.class);

    @Inject
    MeterRegistry registry;

    @Inject
    SecurityIdentity identity;

    /***
     * Page of UAs
     */
    class PageOfUnderpinningAgreements extends Page<UnderpinningAgreement> {
        public PageOfUnderpinningAgreements(int first) { super(first); }
        public PageOfUnderpinningAgreements(int first, int size) { super(first, size); }
    }


    /***
     * Construct with meter
     */
    public UnderpinningAgreements() { super(log); }

    /**
     * List all UAs.
     * @param auth The access token needed to call the service.
     * @param firstItem The first item to return (0-based).
     * @param pageSize The maximum number of items to return.
     * @param allVersions True to return all versions of the items.
     * @return API Response, wraps an ActionSuccess(Page<{@link UnderpinningAgreement}>) or an ActionError entity
     */
    @GET
    @Path("/uas")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.ISM_USER)
    @Operation(operationId = "listUAs",  summary = "List all Underpinning Agreements")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PageOfUnderpinningAgreements.class))),
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
    public Uni<Response> listUAs(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                 @RestQuery("olaId")
                                 @Parameter(required = false,
                                            description = "Filter to the ones supporting specific OLA")
                                 int olaId,
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
                                 boolean allVersions) {

        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("firstItem", firstItem);
        addToDC("pageSize", pageSize);
        addToDC("allVersions", allVersions);

        log.info("Listing UAs");

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Got UA list, success
                log.info("Got UA list");
                var list = new PageOfUnderpinningAgreements(0);
                return Uni.createFrom().item(Response.ok(list).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to list UAs");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Create new UA to support an OLA.
     * @param auth The access token needed to call the service.
     * @param olaId The ID of the OLA to support.
     * @param ua The new UA.
     * @return API Response, wraps an ActionSuccess({@link UnderpinningAgreement}) or an ActionError entity
     */
    @POST
    @Path("/ola/{olaId}/ua")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.UA_OWNER })
    @Operation(operationId = "createUA",  summary = "Create new Underpinning Agreement")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = UnderpinningAgreement.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> createUA(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                  @RestPath("olaId")
                                  @Parameter(required = true, description = "ID of supported Operating Level Agreement")
                                  int olaId,
                                  UnderpinningAgreement ua) {

        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("olaId", olaId);
        addToDC("ua", ua);

        log.info("Creating UA");

        Uni<Response> result = Uni.createFrom().item(new UnderpinningAgreement())

            .chain(created -> {
                // Create complete, success
                log.info("Created UA");
                return Uni.createFrom().item(Response.ok(created).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to create UA");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Update existing UA.
     * @param auth The access token needed to call the service.
     * @param uaId The ID of the UA to update.
     * @param ua The updated UA.
     * @return API Response, wraps an ActionSuccess({@link UnderpinningAgreement}) or an ActionError entity
     */
    @PUT
    @Path("/ua/{uaId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.UA_OWNER })
    @Operation(operationId = "updateUA",  summary = "Update existing Underpinning Agreement",
               description = "Agreements are immutable, once signed. Before an agreement is marked as signed, " +
                             "calling this endpoint just updates the agreement. If you try to update a signed " +
                             "agreement, a new version of the agreement will be created, which can be "+
                             "modified until signed.")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = UnderpinningAgreement.class))),
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
    public Uni<Response> updateUA(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                  @RestPath("uaId")
                                  @Parameter(required = true, description = "ID of agreement to update")
                                  int uaId,
                                  UnderpinningAgreement ua) {

        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("uaId", uaId);
        addToDC("ua", ua);

        log.info("Updating UA");

        Uni<Response> result = Uni.createFrom().item(new UnderpinningAgreement())

            .chain(updated -> {
                // Update complete, success
                log.info("Updated UA");
                return Uni.createFrom().item(Response.ok(updated).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to update UA");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Get existing UA.
     * @param auth The access token needed to call the service.
     * @param uaId The ID of the UA to fetch.
     * @param allVersions True to return all versions.
     * @return API Response, wraps an ActionSuccess({@link UnderpinningAgreement}) or an ActionError entity
     */
    @GET
    @Path("/ua/{uaId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.ISM_USER)
    @Operation(operationId = "getUA",  summary = "Get existing Underpinning Agreement")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = UnderpinningAgreement.class))),
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
    public Uni<Response> fetchUA(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                 @RestPath("uaId")
                                 @Parameter(required = true, description = "ID of agreement to get")
                                 int uaId,
                                 @RestQuery("allVersions") @DefaultValue("false")
                                 @Parameter(required = false, description = "Whether to retrieve all versions")
                                 boolean allVersions) {

        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("uaID", uaId);
        addToDC("allVersions", allVersions);

        log.info("Getting UA");

        Uni<Response> result = Uni.createFrom().item(new UnderpinningAgreement())

            .chain(ua -> {
                // Got UA, success
                log.info("Got UA");
                return Uni.createFrom().item(Response.ok(ua).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to get UA");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Sign existing UA.
     * Agreements are immutable, once signed. After this endpoint is called on an agreement,
     * future updates will create a new version of the agreement that can be modified until signed.
     * @param auth The access token needed to call the service.
     * @param uaId The ID of the UA to sign.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @PATCH
    @Path("/ua/{uaId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.UA_OWNER })
    @Operation(operationId = "signUA",  summary = "Sign existing Underpinning Agreement",
               description = "Agreements are immutable, once signed. After this endpoint is called "+
                             "on an agreement, the next update will create a new version of the agreement, "+
                             "which can be modified until signed.")
    @APIResponses(value = {
            @APIResponse(responseCode = "204", description = "Signed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionSuccess.class))),
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
    public Uni<Response> signUA(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                @RestPath("uaId")
                                @Parameter(required = true, description = "ID of agreement to sign")
                                int uaId) {

        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("uaID", uaId);

        log.info("Signing UA");

        Uni<Response> result = Uni.createFrom().item(new UnderpinningAgreement())

            .chain(signed -> {
                // Sign complete, success
                log.info("Signed UA");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Signed")).status(Response.Status.NO_CONTENT).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to sign UA");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Revoke existing UA.
     * @param auth The access token needed to call the service.
     * @param uaId The ID of the UA to revoke.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @DELETE
    @Path("/ua/{uaId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.UA_OWNER })
    @Operation(operationId = "revokeUA",  summary = "Revoke existing Underpinning Agreement")
    @APIResponses(value = {
            @APIResponse(responseCode = "204", description = "Revoked",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionSuccess.class))),
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
    public Uni<Response> revokeUA(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                  @RestPath("uaId")
                                  @Parameter(required = true, description = "ID of agreement to revoke")
                                  int uaId) {

        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("uaID", uaId);

        log.info("Revoking UA");

        Uni<Response> result = Uni.createFrom().item(new UnderpinningAgreement())

            .chain(revoked -> {
                // Revoke complete, success
                log.info("Revoked UA");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Revoked")).status(Response.Status.NO_CONTENT).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to revoke UA");
                return new ActionError(e).toResponse();
            });

        return result;
    }

}
