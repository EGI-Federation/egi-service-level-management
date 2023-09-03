package egi.eu;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import egi.checkin.InjectMockCheckin;
import egi.eu.model.Role;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.common.QuarkusTestResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.HttpHeaders;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import egi.checkin.MockCheckin;
import egi.checkin.MockCheckinProxy;
import egi.checkin.model.CheckinUser;


@QuarkusTest
@QuarkusTestResource(MockCheckinProxy.class)
public class UsersTest {

    @InjectMockCheckin
    private MockCheckin mockCheckin;

    // The mock Check-in server will accept any token
    private static final String BEARER_TOKEN = UUID.randomUUID().toString();

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

    @Test
    @TestSecurity(user = "test", roles = { Role.IMS_USER})
    @DisplayName("Get user info by VO member")
    public void testGetUserInfo() {
        CheckinUser ui =
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
            .body("entitlements", not(emptyArray()))
            .extract().body().as(CheckinUser.class);

        assertTrue(ui.assurances.contains("Substantial"));
    }

    @Test
    @TestSecurity(user = "test")
    @DisplayName("List users requires VO membership")
    public void testNoListUsers() {
        given()
            .header(HttpHeaders.AUTHORIZATION, "Bearer: " + BEARER_TOKEN)
        .when()
            .get("/users")
        .then()
            .statusCode(Status.FORBIDDEN.getStatusCode());
    }

    @Test
    @TestSecurity(user = "test", roles = { Role.IMS_USER})
    @DisplayName("List all users")
    public void testListUsersInVo() {
        Users.PageOfUsers pu =
        given()
            .header(HttpHeaders.AUTHORIZATION, "Bearer: " + BEARER_TOKEN)
            .queryParam("onlyGroup", "false")
        .when()
            .get("/users")
        .then()
            .statusCode(Status.OK.getStatusCode())
            .body("kind", equalTo("UserList"))
            .body("users", not(emptyArray()))
            .extract().body().as(Users.PageOfUsers.class);
    }

    @Test
    @TestSecurity(user = "test", roles = { Role.IMS_USER})
    @DisplayName("List group members")
    public void testListUsersInGroup() {
        Users.PageOfUsers ul =
        given()
            .header(HttpHeaders.AUTHORIZATION, "Bearer: " + BEARER_TOKEN)
            .queryParam("onlyGroup", "true")
        .when()
            .get("/users")
        .then()
            .statusCode(Status.OK.getStatusCode())
            .body("kind", equalTo("UserList"))
            .body("users", not(emptyArray()))
            .extract().body().as(Users.PageOfUsers.class);
    }

    /***
     * Helper to convert list of entities to a map
     * @param list The list to convert
     * @param mapper The method to call on list elements to get the key to use in the map
     * @return Map of entities indexed by specified key
     * @param <K> Type of the key
     * @param <T> Type of the entities
     */
    private static <K, T> Map<K, T> toMapBy(List<T> list, Function<? super T, ? extends K> mapper) {
        return list.stream().collect(Collectors.toMap(mapper, Function.identity()));
    }
}
