package egi.eu;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Request;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.eclipse.microprofile.config.ConfigProvider;

import org.jboss.logging.Logger;
import jakarta.ws.rs.core.Response.Status;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.Map;

import egi.checkin.MockCheckin;
import egi.checkin.model.UserInfo;


public class UsersTestProxy implements QuarkusTestResourceLifecycleManager {

    private static final Logger log = Logger.getLogger(UsersTestProxy.class);
    private static boolean logBody = true;

    private MockCheckin mockCheckin;
    private static String urlCheckin;
    private static final String pathGetUserInfo = "/auth/realms/egi/protocol/openid-connect/userinfo";
    private static String pathGetVoMembership;
    private static String pathGetGroupMembership;


    /***
     * Construct and load configuration
     */
    public UsersTestProxy() {
        final var config = ConfigProvider.getConfig();
        final var coId = config.getValue("egi.checkin.co-id", String.class);
        final var vo = config.getValue("egi.ims.vo", String.class);;
        final var group = config.getValue("egi.ims.group", String.class);

        pathGetVoMembership = String.format("/api/v2/VoMembers/co/%s/cou/%s.json", coId, vo);
        pathGetGroupMembership = String.format("/api/v2/VoMembers/co/%s/cou/%s.json", coId, group);

        try {
            URL url = new URL(config.getValue("quarkus.oidc.auth-server-url", String.class));
            urlCheckin = new URL(url.getProtocol(), url.getHost(), url.getPort(), "").toString();
        } catch (MalformedURLException e) {
            log.error(e.getMessage());
        }
    }

    /***
     * Start mock server
     * @return Configuration keys to override
     */
    @Override
    public Map<String, String> start() {

        //logBody = false;
        mockCheckin = new MockCheckin(options().port(9092));
        mockCheckin.start();
        configureFor(mockCheckin.getClient());

        // Get user info
        mockCheckin.stubFor(get(urlPathEqualTo(pathGetUserInfo))
            .willReturn(aResponse()
                .withStatus(Status.OK.getStatusCode())
                .withHeader("Content-Type", "application/json")
                .withBody(new UserInfo(12345)
                                .setUserId("423778s7897sd89789fs@egi.eu")
                                .setFullName("John Doe")
                                .addAssurance("https://aai.egi.eu/LoA#Substantial")
                                .addEntitlement("urn:mace:egi.eu:group:vo.access.egi.eu:role=member#aai.egi.eu")
                                .toJsonString())));

        // List users of VO
        mockCheckin.stubFor(get(urlPathEqualTo(pathGetVoMembership))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withStatus(Status.OK.getStatusCode())
                .withBodyFile("checkin.voMembers.json")));

        // List users of group
        mockCheckin.stubFor(get(urlPathEqualTo(pathGetGroupMembership)).inScenario("Users")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withStatus(Status.OK.getStatusCode())
                .withBodyFile("checkin.groupMembers.json")));

        // Let everything else flow to Check-in
        mockCheckin.stubFor(get(urlMatching(".*"))
            .atPriority(10)
            .willReturn(aResponse()
                .proxiedFrom(urlCheckin)));

        // Add logging of mocked requests
        mockCheckin.addMockServiceRequestListener(UsersTestProxy::requestReceived);

        return Collections.singletonMap("egi.checkin.server", mockCheckin.baseUrl());
    }

    /***
     * Shutdown mock server
     */
    @Override
    public void stop() {
        if(null != mockCheckin)
            mockCheckin.stop();
    }

    /***
     * Inject this mock server to test class using custom annotation @InjectWireMock
     */
    @Override
    public void inject(TestInjector testInjector) {
        testInjector.injectIntoFields(mockCheckin, new TestInjector.AnnotatedAndMatchesType(InjectWireMockUsers.class, WireMockServer.class));
    }

    /**
     * Logs information from supplied WireMock request and response objects.
     * If no response was matched, payload will be null and there will be no response headers.
     * @param inRequest The received request
     * @param inResponse The selected response
     */
    protected static void requestReceived(Request inRequest, com.github.tomakehurst.wiremock.http.Response inResponse) {
        log.info("Check-in request: " + inRequest.getAbsoluteUrl());
        log.info("Check-in request headers:\n" + inRequest.getHeaders());
        if(logBody)
            log.info("Check-in response body:\n" + inResponse.getBodyAsString());
        log.info("Check-in response headers:\n" + inResponse.getHeaders());
    }
}
