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
    Uni<UserInfo> getUserInfoAsync(@RestHeader("Authorization") String auth);

    @GET
    @Path("/registry/cous.json")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<CheckinGroupList> listAllGroupsAsync(@RestHeader("Authorization") String auth,
                                             @RestQuery("coid") String coId);

    @GET
    @Path("/api/v2/VoMembers/co/{coId}/cou/{groupName}.json")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<CheckinRoleList> listGroupMembersAsync(@RestHeader("Authorization") String auth,
                                               @RestPath("coId") String coId,
                                               @RestPath("groupName") String groupName);


}
