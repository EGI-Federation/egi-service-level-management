package egi.eu;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.RestHeader;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.mutiny.core.Vertx;
import io.smallrye.mutiny.Uni;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;

import java.util.List;

import egi.checkin.model.CheckinUser;
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

    // Parameter(s) to add to all endpoints
    @RestHeader(TEST_STUB)
    @Parameter(hidden = true)
    @Schema(defaultValue = "default")
    String stub;


    /***
     * Page of OLAs
     */
    static class PageOfOperationalLevelAgreements extends Page<OperationalLevelAgreement> {
        public PageOfOperationalLevelAgreements(String baseUri, long offset, long limit, List<OperationalLevelAgreement> olas) {
            super(baseUri, offset, limit, olas); }
    }


    /***
     * Constructor
     */
    public OperationalLevelAgreements() { super(log); }

    /**
     * List all OLAs.
     * @param auth The access token needed to call the service.
     * @param slaId If provided, will only return OLAs supporting the SLA with this Id
     * @param offset The number of elements to skip
     * @param limit_ The maximum number of elements to return
     * @param allVersions True to return all versions of the items.
     * @return API Response, wraps an ActionSuccess(Page<{@link OperationalLevelAgreement}>) or an ActionError entity
     */
    @GET
    @Path("/olas")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.IMS_USER)
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
                                  @Context UriInfo uriInfo,
                                  @Context HttpHeaders httpHeaders,

                                  @RestQuery("slaId")
                                  @Parameter(description = "Filter to the ones supporting specific SLA")
                                  int slaId,

                                  @RestQuery("allVersions")
                                  @Parameter(description = "Whether to retrieve all versions")
                                  @Schema(defaultValue = "false")
                                  boolean allVersions,

                                  @RestQuery("offset")
                                  @Parameter(description = "Skip the first given number of results")
                                  @Schema(defaultValue = "0")
                                  long offset,

                                  @RestQuery("limit")
                                  @Parameter(description = "Restrict the number of results returned")
                                  @Schema(defaultValue = "100")
                                  long limit_)
    {
        final long limit = (0 == limit_) ? 100 : limit_;

        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("allVersions", allVersions);
        addToDC("offset", offset);
        addToDC("limit", limit);

        log.info("Listing OLAs");

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Got OLA list, success
                log.info("Got OLA list");
                var uri = getRealRequestUri(uriInfo, httpHeaders);
                var page = new PageOfOperationalLevelAgreements(uri.toString(), offset, limit, null);
                return Uni.createFrom().item(Response.ok(page).build());
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
    @Consumes(MediaType.APPLICATION_JSON)
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
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
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
    @Consumes(MediaType.APPLICATION_JSON)
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
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
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
    @RolesAllowed(Role.IMS_USER)
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

                                  @RestQuery("allVersions")
                                  @Parameter(description = "Whether to retrieve all versions")
                                  @Schema(defaultValue = "false")
                                  boolean allVersions)
    {
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
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
            @APIResponse(responseCode = "200", description = "Signed",
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
    public Uni<Response> signOLA(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                 @RestPath("olaId")
                                 @Parameter(required = true, description = "ID of agreement to sign")
                                 int olaId)
    {
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("olaID", olaId);

        log.info("Signing OLA");

        Uni<Response> result = Uni.createFrom().item(new OperationalLevelAgreement())

            .chain(signed -> {
                // Sign complete, success
                log.info("Signed OLA");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Signed")).build());
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
            @APIResponse(responseCode = "200", description = "Revoked",
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
    public Uni<Response> revokeOLA(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                   @RestPath("olaId")
                                   @Parameter(required = true, description = "ID of agreement to revoke")
                                   int olaId)
    {
        addToDC("userIdCaller", identity.getAttribute(CheckinUser.ATTR_USERID));
        addToDC("userNameCaller", identity.getAttribute(CheckinUser.ATTR_FULLNAME));
        addToDC("olaID", olaId);

        log.info("Revoking OLA");

        Uni<Response> result = Uni.createFrom().item(new OperationalLevelAgreement())

            .chain(revoked -> {
                // Revoke complete, success
                log.info("Revoked OLA");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Revoked")).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to revoke OLA");
                return new ActionError(e).toResponse();
            });

        return result;
    }

}
