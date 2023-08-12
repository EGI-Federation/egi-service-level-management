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
import jakarta.ws.rs.core.*;
import java.util.List;

import egi.checkin.model.UserInfo;
import egi.eu.model.*;


/***
 * Resource for Service Level Agreement (SLA) queries and operations.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class ServiceLevelAgreements extends BaseResource {

    private static final Logger log = Logger.getLogger(ServiceLevelAgreements.class);

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
     * Page of SLAs
     */
    static class PageOfServiceLevelAgreements extends Page<ServiceLevelAgreement> {
        public PageOfServiceLevelAgreements(String baseUri, long offset, long limit, List<ServiceLevelAgreement> slas) {
            super(baseUri, offset, limit, slas); }
    }


    /***
     * Construct with meter
     */
    public ServiceLevelAgreements() { super(log); }

    /**
     * List all SLAs.
     * @param auth The access token needed to call the service.
     * @param offset The number of elements to skip
     * @param limit The maximum number of elements to return
     * @param allVersions True to return all versions of the items.
     * @return API Response, wraps an ActionSuccess(Page<{@link ServiceLevelAgreement}>) or an ActionError entity
     */
    @GET
    @Path("/slas")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.IMS_USER)
    @Operation(operationId = "listSLAs",  summary = "List all Service Level Agreements")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Success",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PageOfServiceLevelAgreements.class))),
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
    public Uni<Response> listSLAs(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,
                                  @Context UriInfo uriInfo,
                                  @Context HttpHeaders httpHeaders,

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
                                  long limit) {

        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("allVersions", allVersions);
        addToDC("offset", offset);
        addToDC("limit", limit);

        log.info("Listing SLAs");

        Uni<Response> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Got SLA list, success
                log.info("Got SLA list");
                var uri = getRealRequestUri(uriInfo, httpHeaders);
                var page = new PageOfServiceLevelAgreements(uri.toString(), offset, limit, null);
                return Uni.createFrom().item(Response.ok(page).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to list SLAs");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Create new SLA.
     * @param auth The access token needed to call the service.
     * @param sla The new SLA.
     * @return API Response, wraps an ActionSuccess({@link ServiceLevelAgreement}) or an ActionError entity
     */
    @POST
    @Path("/sla")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.SLA_OWNER })
    @Operation(operationId = "createSLA",  summary = "Create new Service Level Agreement")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ServiceLevelAgreement.class))),
            @APIResponse(responseCode = "400", description="Invalid parameters or configuration",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ActionError.class))),
            @APIResponse(responseCode = "401", description="Authorization required"),
            @APIResponse(responseCode = "403", description="Permission denied"),
            @APIResponse(responseCode = "503", description="Try again later")
    })
    public Uni<Response> createSLA(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                   ServiceLevelAgreement sla) {

        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("sla", sla);

        log.info("Creating SLA");

        Uni<Response> result = Uni.createFrom().item(new ServiceLevelAgreement())

            .chain(created -> {
                // Create complete, success
                log.info("Created SLA");
                return Uni.createFrom().item(Response.ok(created).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to create SLA");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Update existing SLA.
     * @param auth The access token needed to call the service.
     * @param slaId The ID of the SLA to update.
     * @param sla The updated SLA.
     * @return API Response, wraps an ActionSuccess({@link ServiceLevelAgreement}) or an ActionError entity
     */
    @PUT
    @Path("/sla/{slaId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.SLA_OWNER })
    @Operation(operationId = "updateSLA",  summary = "Update existing Service Level Agreement",
               description = "Agreements are immutable, once signed. Before an agreement is marked as signed, " +
                             "calling this endpoint just updates the agreement. If you try to update a signed " +
                             "agreement, a new version of the agreement will be created, which can be "+
                             "modified until signed.")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ServiceLevelAgreement.class))),
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
    public Uni<Response> updateSLA(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                   @RestPath("slaId")
                                   @Parameter(required = true, description = "ID of agreement to update")
                                   int slaId,

                                   ServiceLevelAgreement sla) {

        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("slaId", slaId);
        addToDC("sla", sla);

        log.info("Updating SLA");

        Uni<Response> result = Uni.createFrom().item(new ServiceLevelAgreement())

            .chain(created -> {
                // Update complete, success
                log.info("Updated SLA");
                return Uni.createFrom().item(Response.ok(created).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to update SLA");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Get existing SLA.
     * @param auth The access token needed to call the service.
     * @param slaId The ID of the SLA to fetch.
     * @param allVersions True to return all versions.
     * @return API Response, wraps an ActionSuccess({@link ServiceLevelAgreement}) or an ActionError entity
     */
    @GET
    @Path("/sla/{slaId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed(Role.IMS_USER)
    @Operation(operationId = "getSLA",  summary = "Get existing Service Level Agreement")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ServiceLevelAgreement.class))),
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
    public Uni<Response> fetchSLA(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                  @RestPath("slaId")
                                  @Parameter(required = true, description = "ID of agreement to get")
                                  int slaId,

                                  @RestQuery("allVersions")
                                  @Parameter(description = "Whether to retrieve all versions")
                                  @Schema(defaultValue = "false")
                                  boolean allVersions) {

        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("slaId", slaId);
        addToDC("allVersions", allVersions);

        log.info("Getting SLA");

        Uni<Response> result = Uni.createFrom().item(new ServiceLevelAgreement())

            .chain(sla -> {
                // Got SLA, success
                log.info("Got SLA");
                return Uni.createFrom().item(Response.ok(sla).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to get SLA");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Sign existing SLA.
     * Agreements are immutable, once signed. After this endpoint is called on an agreement,
     * future updates will create a new version of the agreement that can be modified until signed.
     * @param auth The access token needed to call the service.
     * @param slaId The ID of the UA to sign.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @PATCH
    @Path("/sla/{slaId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.SLA_OWNER })
    @Operation(operationId = "signSLA",  summary = "Sign existing Service Level Agreement",
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
    public Uni<Response> signSLA(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                 @RestPath("slaId")
                                 @Parameter(required = true, description = "ID of agreement to sign")
                                 int slaId) {

        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("slaId", slaId);

        log.info("Signing SLA");

        Uni<Response> result = Uni.createFrom().item(new ServiceLevelAgreement())

            .chain(signed -> {
                // Sign complete, success
                log.info("Signed SLA");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Signed")).status(Response.Status.NO_CONTENT).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to sign SLA");
                return new ActionError(e).toResponse();
            });

        return result;
    }

    /**
     * Revoke existing SLA.
     * @param auth The access token needed to call the service.
     * @param slaId The ID of the SLA to revoke.
     * @return API Response, wraps an ActionSuccess or an ActionError entity
     */
    @DELETE
    @Path("/sla/{slaId}")
    @SecurityRequirement(name = "OIDC")
    @RolesAllowed({ Role.PROCESS_OWNER, Role.PROCESS_MANAGER, Role.SLA_OWNER })
    @Operation(operationId = "revokeSLA",  summary = "Revoke existing Service Level Agreement")
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
    public Uni<Response> revokeSLA(@RestHeader(HttpHeaders.AUTHORIZATION) String auth,

                                   @RestPath("slaId")
                                   @Parameter(required = true, description = "ID of agreement to revoke")
                                   int slaId) {

        addToDC("userId", identity.getAttribute(UserInfo.ATTR_USERID));
        addToDC("userName", identity.getAttribute(UserInfo.ATTR_USERNAME));
        addToDC("slaId", slaId);

        log.info("Revoking SLA");

        Uni<Response> result = Uni.createFrom().item(new ServiceLevelAgreement())

            .chain(revoked -> {
                // Revoke complete, success
                log.info("Revoked SLA");
                return Uni.createFrom().item(Response.ok(new ActionSuccess("Revoked")).status(Response.Status.NO_CONTENT).build());
            })
            .onFailure().recoverWithItem(e -> {
                log.error("Failed to revoke SLA");
                return new ActionError(e).toResponse();
            });

        return result;
    }

}
