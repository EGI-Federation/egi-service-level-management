package egi.eu;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.*;

import egi.checkin.model.UserInfo;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.common.QuarkusTestResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.UUID;


@QuarkusTest
@QuarkusTestResource(UsersTestProxy.class)
public class UsersTest {

    // The mock Check-in server will accept any token
    private static final String BEARER_TOKEN = UUID.randomUUID().toString();

    @Test
    @TestSecurity(user = "test", roles = { Role.ISM_USER })
    @DisplayName("Get user info by VO member")
    public void testGetUserInfo() {
        UserInfo ui =
        given()
            .header(HttpHeaders.AUTHORIZATION, "Bearer: " + BEARER_TOKEN)
        .when()
            .get("/user/info")
        .then()
            .statusCode(Status.OK.getStatusCode())
            .body("kind", equalTo("UserInfo"))
            .body("checkinUserId", equalTo(12345))
            .body("voperson_id", matchesPattern("^\\w+@egi.eu"))
            .body("assurances", not(emptyArray()))
            .body("assurances", not(emptyArray()))
            .body("entitlements", not(emptyArray()))
            .extract().body().as(UserInfo.class);
    }

    @Test
    @TestSecurity(user = "test")
    @DisplayName("Get user info requires VO membership")
    public void testNoGetUserInfo() {
        given()
            .header(HttpHeaders.AUTHORIZATION, "Bearer: " + BEARER_TOKEN)
        .when()
            .get("/user/info")
        .then()
            .statusCode(Status.FORBIDDEN.getStatusCode());
    }
}
