package egi.checkin;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.Options;


public class SlmMockCheckin extends WireMockServer {
    public SlmMockCheckin(Options options) {
        super(options);
    }

    public WireMock getClient() {
        return client;
    }
}
