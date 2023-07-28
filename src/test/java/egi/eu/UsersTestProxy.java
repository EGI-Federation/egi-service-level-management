package egi.eu;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.eclipse.microprofile.config.ConfigProvider;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.HttpHeaders;

import egi.checkin.model.UserInfo;


public class UsersTestProxy implements QuarkusTestResourceLifecycleManager {

    private WireMockServer mockCheckin;
    private String urlCheckin;
    private static final String pathGetUserInfo = "/auth/realms/egi/protocol/openid-connect/userinfo";

    @Override
    public Map<String, String> start() {

        try {
            URL url = new URL(ConfigProvider.getConfig().getValue("quarkus.oidc.auth-server-url", String.class));
            urlCheckin = new URL(url.getProtocol(), url.getHost(), url.getPort(), "").toString();
        } catch (MalformedURLException e) {}


        mockCheckin = new WireMockServer();
        mockCheckin.start();

        // Get user info
        stubFor(get(urlPathEqualTo(pathGetUserInfo))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(new UserInfo(12345)
                                .setUserId("423778s7897sd89789fs@egi.eu")
                                .setFullName("John Doe")
                                .addAssurance("https://aai.egi.eu/LoA#Substantial")
                                .addEntitlement("urn:mace:egi.eu:group:vo.access.egi.eu:role=member#aai.egi.eu")
                                .toJsonString())));

        // Let everything else flow to Check-in
        stubFor(get(urlMatching(".*"))
            .atPriority(10)
            .willReturn(aResponse()
                .proxiedFrom(this.urlCheckin)));

        return Collections.singletonMap("egi.checkin.server", mockCheckin.baseUrl());
    }

    @Override
    public void stop() {
        if(null != mockCheckin)
            mockCheckin.stop();
    }
}
