package egi.checkin;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestQuery;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

import egi.checkin.model.*;


/***
 * REST client for EGI Check-in
 */
@RegisterProvider(value = CheckinServiceExceptionMapper.class)
@Produces(MediaType.APPLICATION_JSON)
@Path("/auth/realms/egi")
public interface CheckinService {

    @GET
    @Path("/protocol/openid-connect/userinfo")
    Uni<UserInfo> getUserInfoAsync(@RestHeader("Authorization") String auth);
}
