package egi.checkin;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import egi.checkin.model.*;


/***
 * REST client for EGI Check-in
 */
@RegisterProvider(value = CheckinServiceExceptionMapper.class)
public interface CheckinService {

    @GET
    @Path("/auth/realms/egi/protocol/openid-connect/userinfo")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<CheckinUser> getUserInfoAsync(@RestHeader("Authorization") String auth,
                                      @RestHeader("x-test-stub") String stub);

    @GET
    @Path("/registry/cous.json")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<CheckinGroupList> listAllGroupsAsync(@RestHeader("Authorization") String auth,
                                             @RestHeader("x-test-stub") String stub,
                                             @RestQuery("coid") String coId);

    @GET
    @Path("/api/v2/VoMembers/co/{coId}/cou/{groupName}.json")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<CheckinRoleList> listGroupMembersAsync(@RestHeader("Authorization") String auth,
                                               @RestHeader("x-test-stub") String stub,
                                               @RestPath("coId") String coId,
                                               @RestPath("groupName") String groupName);

    @POST
    @Path("/api/v2/VoMembers.json")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<CheckinObject> addUserRoleAsync(@RestHeader("Authorization") String auth,
                                        @RestHeader("x-test-stub") String stub,
                                        CheckinRoleList addRoles);

    @PUT
    @Path("/api/v2/VoMembers/{roleId}.json")
    @Consumes(MediaType.APPLICATION_JSON)
    Uni<String> updateUserRoleAsync(@RestHeader("Authorization") String auth,
                                    @RestHeader("x-test-stub") String stub,
                                    @RestPath("roleId") long roleId,
                                    CheckinRoleList updateRoles);
}
