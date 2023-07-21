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
 * Resource for Operational Level Agreement (OLA) queries and operations.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class OperationalLevelAgreements extends BaseResource {

    private static final Logger log = Logger.getLogger(OperationalLevelAgreements.class);

    @Inject
    MeterRegistry registry;

    @Inject
    SecurityIdentity identity;

    /***
     * Page of OLAs
     */
    class PageOfOperationalLevelAgreements extends Page<OperationalLevelAgreement> {
        public PageOfOperationalLevelAgreements(int first) { super(first); }
        public PageOfOperationalLevelAgreements(int first, int size) { super(first, size); }
    }


    /***
     * Construct with meter
     */
    public OperationalLevelAgreements() { super(log); }

    /**
     * List all OLAs.
     * @param auth The access token needed to call the service.
     * @param firstItem The first item to return (0-based).
     * @param pageSize The maximum number of items to return.
     * @param allVersions True to return all versions of the items.
     * @return API Response, wraps an ActionSuccess(Page<{@link OperationalLevelAgreement}>) or an ActionError entity
     */
    @GET
    @Path("/ola/list")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.ISM_USER)
    @Operation(operationId = "listOLAs",  summary = "List all Operational Level Agreements")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PageOfOperationalLevelAgreements.class))),
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
    public Uni<Response> listOLAs(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                  @RestQuery("slaId")
                                  @Parameter(required = false,
                                             description = "Filter to the ones supporting specific SLA")
                                  int slaId,
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

        log.info("Listing OLAs");

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Got OLA list, success
                log.info("Got OLA list");
                var list = new PageOfOperationalLevelAgreements(0);
                return Uni.createFrom().item(Response.ok(list).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to list OLAs");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Create new OLA to support an SLA.
     * @param auth The access token needed to call the service.
     * @param slaId The ID of the SLA to support.
     * @param ola The new OLA.
     * @return API Response, wraps an ActionSuccess({@link OperationalLevelAgreement}) or an ActionError entity
     */
    @POST
    @Path("/sla/{slaId}/ola")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.OLA_OWNER })
    @Operation(operationId = "createOLA",  summary = "Create new Operational Level Agreement")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = OperationalLevelAgreement.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> createOLA(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                   @RestPath("slaId")
                                   @Parameter(required = true, description = "ID of supported Service Level Agreement")
                                   int slaId,
                                   OperationalLevelAgreement ola)
    {
        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("slaId", slaId);
        addToDC("ola", ola);

        log.info("Creating OLA");

        Uni<Response> result = Uni.createFrom().item(new OperationalLevelAgreement())

            .chain(created -> {
                // Create complete, success
                log.info("Created OLA");
                return Uni.createFrom().item(Response.ok(created).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to create OLA");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Update existing OLA.
     * @param auth The access token needed to call the service.
     * @param olaId The ID of the OLA to update.
     * @param ola The updated OLA.
     * @return API Response, wraps an ActionSuccess({@link OperationalLevelAgreement}) or an ActionError entity
     */
    @PUT
    @Path("/ola/{olaId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.OLA_OWNER })
    @Operation(operationId = "updateOLA",  summary = "Update existing Operational Level Agreement",
               description = "Agreements are immutable, once signed. Before an agreement is marked as signed, " +
                             "calling this endpoint just updates the agreement. If you try to update a signed " +
                             "agreement, a new version of the agreement will be created, which can be "+
                             "modified until signed.")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = OperationalLevelAgreement.class))),
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
    public Uni<Response> updateOLA(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                   @RestPath("olaId")
                                   @Parameter(required = true, description = "ID of agreement to update")
                                   int olaId,
                                   OperationalLevelAgreement ola)
    {
        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("olaId", olaId);
        addToDC("ola", ola);

        log.info("Updating OLA");

        Uni<Response> result = Uni.createFrom().item(new OperationalLevelAgreement())

            .chain(created -> {
                // Update complete, success
                log.info("Updated OLA");
                return Uni.createFrom().item(Response.ok(created).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to update OLA");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Get existing OLA.
     * @param auth The access token needed to call the service.
     * @param olaId The ID of the OLA to fetch.
     * @param allVersions True to return all versions.
     * @return API Response, wraps an ActionSuccess({@link OperationalLevelAgreement}) or an ActionError entity
     */
    @GET
    @Path("/ola/{olaId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.ISM_USER)
    @Operation(operationId = "getOLA",  summary = "Get existing Operational Level Agreement")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = OperationalLevelAgreement.class))),
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
    public Uni<Response> fetchOLA(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                  @RestPath("olaId")
                                  @Parameter(required = true, description = "ID of agreement to get")
                                  int olaId,
                                  @RestQuery("allVersions") @DefaultValue("false")
                                  @Parameter(required = false, description = "Whether to retrieve all versions")
                                  boolean allVersions)
    {
        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("olaID", olaId);
        addToDC("allVersions", allVersions);

        log.info("Getting OLA");

        Uni<Response> result = Uni.createFrom().item(new OperationalLevelAgreement())

            .chain(created -> {
                // Got OLA, success
                log.info("Got OLA");
                return Uni.createFrom().item(Response.ok(created).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to get OLA");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Sign existing OLA.
     * Agreements are immutable, once signed. After this endpoint is called on an agreement,
     * future updates will create a new version of the agreement that can be modified until signed.
     * @param auth The access token needed to call the service.
     * @param olaId The ID of the UA to sign.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @PATCH
    @Path("/ola/{olaId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.OLA_OWNER })
    @Operation(operationId = "signOLA",  summary = "Sign existing Operational Level Agreement",
               description = "Agreements are immutable, once signed. After this endpoint is called "+
                             "on an agreement, the next update will create a new version of the agreement, "+
                             "which can be modified until signed.")
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
    public Uni<Response> signOLA(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                 @RestPath("olaId")
                                 @Parameter(required = true, description = "ID of agreement to sign")
                                 int olaId)
    {
        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("olaID", olaId);

        log.info("Signing OLA");

        Uni<Response> result = Uni.createFrom().item(new OperationalLevelAgreement())

            .chain(signed -> {
                // Sign complete, success
                log.info("Signed OLA");
                return Uni.createFrom().item(Response.ok().status(Status.NO_CONTENT).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to sign OLA");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Revoke existing OLA.
     * @param auth The access token needed to call the service.
     * @param olaId The ID of the OLA to revoke.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @DELETE
    @Path("/ola/{olaId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.OLA_OWNER })
    @Operation(operationId = "revokeOLA",  summary = "Revoke existing Operational Level Agreement")
    @APIResponses(value = {
            @APIResponse(responseCode = "204", description = "Revoked"),
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
    public Uni<Response> revokeOLA(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                   @RestPath("olaId")
                                   @Parameter(required = true, description = "ID of agreement to revoke")
                                   int olaId)
    {
        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("olaID", olaId);

        log.info("Revoking OLA");

        Uni<Response> result = Uni.createFrom().item(new OperationalLevelAgreement())

            .chain(revoked -> {
                // Revoke complete, success
                log.info("Revoked OLA");
                return Uni.createFrom().item(Response.ok().status(Status.NO_CONTENT).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to revoke OLA");
                return new ActionError(e).toResponse();
            });

        return result;
    }

}
