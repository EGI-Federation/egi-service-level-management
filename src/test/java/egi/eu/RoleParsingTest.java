package egi.eu;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import egi.eu.model.Role;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;

import egi.checkin.model.UserInfo;


@QuarkusTest
public class RoleParsingTest {

    @Inject
    IntegratedManagementSystemConfig imsConfig;

    private static final Logger log = Logger.getLogger(RoleParsingTest.class);
    private String prefix;
    private final String postfix = "#aai.egi.eu";
    private final String imso = "ims-owner";
    private final String imsm = "ims-manager";
    private final String imsc = "ims-coordinator";
    private String po, pm, cm, ro, uao, olao, slao;
    private Map<String, String> roleNames = new HashMap<String, String>();
    private UserInfo userInfo;
    private QuarkusSecurityIdentity.Builder builder;
    private RoleCustomization roleCustomization;
    private ObjectMapper mapper = new ObjectMapper();


    @BeforeEach
    public void setup() {
        prefix = "urn:mace:egi.eu:group:" + imsConfig.vo() + ":";
        if(roleNames.isEmpty()) {
            roleNames.putAll(imsConfig.roles());

            po = roleNames.get("process-owner").toLowerCase();
            pm = roleNames.get("process-manager").toLowerCase();
            cm = roleNames.get("catalog-manager").toLowerCase();
            ro = roleNames.get("report-owner").toLowerCase();
            uao = roleNames.get("ua-owner").toLowerCase();
            olao = roleNames.get("ola-owner").toLowerCase();
            slao = roleNames.get("sla-owner").toLowerCase();
        }

        roleCustomization = new RoleCustomization();
        roleCustomization.setConfig(imsConfig);

        userInfo = new UserInfo();
        userInfo.userId = "e9c37aa0d1cf14c56e560f9f9915da6761f54383badb501a2867bc43581b835c@egi.eu";
        userInfo.addEntitlement("urn:mace:egi.eu:group:vo.access.egi.eu:role=member#aai.egi.eu");
        userInfo.addEntitlement("urn:mace:egi.eu:group:vo.access.egi.eu:role=vm_operator#aai.egi.eu");

        builder = QuarkusSecurityIdentity.builder();
        var principal = new QuarkusPrincipal("test");
        builder.setPrincipal(principal);
    }

    @Test
    @DisplayName("All roles require explicit VO membership")
    public void testNoVoMembership() {
        // Setup entitlements
        userInfo.addEntitlement(prefix + "admins:role=member" + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=member", imsConfig.group()) + postfix);
        userInfo.addEntitlement(prefix + String.format("IMS:role=%s", imso) + postfix);
        userInfo.addEntitlement(prefix + String.format("IMS:role=%s", imsm) + postfix);
        userInfo.addEntitlement(prefix + String.format("IMS:role=%s", imsc) + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), po) + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), pm) + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), cm) + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), ro) + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), uao) + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), olao) + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), slao) + postfix);

        try {
            builder.addAttribute("userinfo", mapper.writeValueAsString(userInfo));
        } catch (JsonProcessingException e) {
            fail(e.getMessage());
        }

        // Parse roles from entitlements
        UniAssertSubscriber<Boolean> subscriber = this.roleCustomization.augment(this.builder.build(), null)
            .onItem().transform(id -> id.getRoles())
            .onItem().transform(roles -> {
                // Check that it does not have any of the roles
                return roles.contains(Role.IMS_USER) ||
                       roles.contains(Role.IMS_ADMIN) ||
                       roles.contains(Role.PROCESS_OWNER) ||
                       roles.contains(Role.PROCESS_MANAGER) ||
                       roles.contains(Role.CATALOG_MANAGER) ||
                       roles.contains(Role.REPORT_OWNER) ||
                       roles.contains(Role.UA_OWNER) ||
                       roles.contains(Role.OLA_OWNER) ||
                       roles.contains(Role.SLA_OWNER);
            })
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());

        subscriber
            .awaitItem()
            .assertItem(false);
    }

    @Test
    @DisplayName("ISM_USER when VO member")
    public void testVoMembership() {
        // Setup entitlements
        userInfo.addEntitlement(prefix + "role=member" + postfix);

        try {
            builder.addAttribute("userinfo", mapper.writeValueAsString(userInfo));
        } catch (JsonProcessingException e) {
            fail(e.getMessage());
        }

        // Parse roles from entitlements
        UniAssertSubscriber<Boolean> subscriber = this.roleCustomization.augment(this.builder.build(), null)
            .onItem().transform(id -> id.getRoles())
            .onItem().transform(roles -> {
                // Check that it has the correct role
                return roles.contains(Role.IMS_USER);
            })
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());

        subscriber
            .awaitItem()
            .assertItem(true);
    }

    @Test
    @DisplayName("ISM_ADMIN requires explicit IMS group membership")
    public void testNoImsGroupAdmin() {
        // Setup entitlements
        userInfo.addEntitlement(prefix + "role=member" + postfix);
        userInfo.addEntitlement(prefix + String.format("IMS:role=%s", imso) + postfix);
        userInfo.addEntitlement(prefix + String.format("IMS:role=%s", imsm) + postfix);
        userInfo.addEntitlement(prefix + String.format("IMS:role=%s", imsc) + postfix);

        try {
            builder.addAttribute("userinfo", mapper.writeValueAsString(userInfo));
        } catch (JsonProcessingException e) {
            fail(e.getMessage());
        }

        // Parse roles from entitlements
        UniAssertSubscriber<Boolean> subscriber = this.roleCustomization.augment(this.builder.build(), null)
                .onItem().transform(id -> id.getRoles())
                .onItem().transform(roles -> {
                    // Check that it does not have the role
                    return roles.contains(Role.IMS_ADMIN);
                })
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create());

        subscriber
                .awaitItem()
                .assertItem(false);
    }

    @Test
    @DisplayName("ISM_ADMIN when VO member and included in IMS group")
    public void testImsOwnerIsAdmin() {
        // Setup entitlements
        userInfo.addEntitlement(prefix + "role=member" + postfix);
        userInfo.addEntitlement(prefix + "IMS:role=member" + postfix);
        userInfo.addEntitlement(prefix + String.format("IMS:role=%s", imso) + postfix);

        try {
            builder.addAttribute("userinfo", mapper.writeValueAsString(userInfo));
        } catch (JsonProcessingException e) {
            fail(e.getMessage());
        }

        // Parse roles from entitlements
        UniAssertSubscriber<Boolean> subscriber = this.roleCustomization.augment(this.builder.build(), null)
            .onItem().transform(id -> id.getRoles())
            .onItem().transform(roles -> {
                // Check that it has the correct roles
                return roles.contains(Role.IMS_USER) &&
                       roles.contains(Role.IMS_ADMIN);
            })
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());

        subscriber
            .awaitItem()
            .assertItem(true);
    }

    @Test
    @DisplayName("ISM_ADMIN when VO member and included in IMS group")
    public void testImsManagerIsAdmin() {
        // Setup entitlements
        userInfo.addEntitlement(prefix + "role=member" + postfix);
        userInfo.addEntitlement(prefix + "IMS:role=member" + postfix);
        userInfo.addEntitlement(prefix + String.format("IMS:role=%s", imsm) + postfix);

        try {
            builder.addAttribute("userinfo", mapper.writeValueAsString(userInfo));
        } catch (JsonProcessingException e) {
            fail(e.getMessage());
        }

        // Parse roles from entitlements
        UniAssertSubscriber<Boolean> subscriber = this.roleCustomization.augment(this.builder.build(), null)
                .onItem().transform(id -> id.getRoles())
                .onItem().transform(roles -> {
                    // Check that it has the correct roles
                    return roles.contains(Role.IMS_USER) &&
                           roles.contains(Role.IMS_ADMIN);
                })
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create());

        subscriber
                .awaitItem()
                .assertItem(true);
    }

    @Test
    @DisplayName("ISM_ADMIN when VO member and included in IMS group")
    public void testImsCoordinatorIsAdmin() {
        // Setup entitlements
        userInfo.addEntitlement(prefix + "role=member" + postfix);
        userInfo.addEntitlement(prefix + "IMS:role=member" + postfix);
        userInfo.addEntitlement(prefix + String.format("IMS:role=%s", imsc) + postfix);

        try {
            builder.addAttribute("userinfo", mapper.writeValueAsString(userInfo));
        } catch (JsonProcessingException e) {
            fail(e.getMessage());
        }

        // Parse roles from entitlements
        UniAssertSubscriber<Boolean> subscriber = this.roleCustomization.augment(this.builder.build(), null)
                .onItem().transform(id -> id.getRoles())
                .onItem().transform(roles -> {
                    // Check that it has the correct roles
                    return roles.contains(Role.IMS_USER) &&
                           roles.contains(Role.IMS_ADMIN);
                })
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create());

        subscriber
                .awaitItem()
                .assertItem(true);
    }

    @Test
    @DisplayName("SLM roles require explicit SLM group membership")
    public void testNoGroupMembership() {
        // Setup entitlements
        userInfo.addEntitlement(prefix + "role=member" + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), po) + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), pm) + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), cm) + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), ro) + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), uao) + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), olao) + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), slao) + postfix);

        try {
            builder.addAttribute("userinfo", mapper.writeValueAsString(userInfo));
        } catch (JsonProcessingException e) {
            fail(e.getMessage());
        }

        // Parse roles from entitlements
        UniAssertSubscriber<Boolean> subscriber = this.roleCustomization.augment(this.builder.build(), null)
            .onItem().transform(id -> id.getRoles())
            .onItem().transform(roles -> {
                // Check that it does not have any of the roles
                return roles.contains(Role.PROCESS_OWNER) ||
                       roles.contains(Role.PROCESS_MANAGER) ||
                       roles.contains(Role.CATALOG_MANAGER) ||
                       roles.contains(Role.REPORT_OWNER) ||
                       roles.contains(Role.UA_OWNER) ||
                       roles.contains(Role.OLA_OWNER) ||
                       roles.contains(Role.SLA_OWNER);
            })
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());

        subscriber
            .awaitItem()
            .assertItem(false);
    }

    @Test
    @DisplayName("PROCESS_OWNER requires both VO and SLM group membership")
    public void testProcessOwner() {
        // Setup entitlements
        userInfo.addEntitlement(prefix + "role=member" + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=member", imsConfig.group()) + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), po) + postfix);

        try {
            builder.addAttribute("userinfo", mapper.writeValueAsString(userInfo));
        } catch (JsonProcessingException e) {
            fail(e.getMessage());
        }

        // Parse roles from entitlements
        UniAssertSubscriber<Boolean> subscriber = this.roleCustomization.augment(this.builder.build(), null)
            .onItem().transform(id -> id.getRoles())
            .onItem().transform(roles -> {
                // Check that it have the correct role
                return roles.contains(Role.PROCESS_OWNER) &&
                       roles.contains(Role.IMS_USER);
            })
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());

        subscriber
            .awaitItem()
            .assertItem(true);
    }

    @Test
    @DisplayName("PROCESS_MANAGER requires both VO and SLM group membership")
    public void testProcessManager() {
        // Setup entitlements
        userInfo.addEntitlement(prefix + "role=member" + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=member", imsConfig.group()) + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), pm) + postfix);

        try {
            builder.addAttribute("userinfo", mapper.writeValueAsString(userInfo));
        } catch (JsonProcessingException e) {
            fail(e.getMessage());
        }

        // Parse roles from entitlements
        UniAssertSubscriber<Boolean> subscriber = this.roleCustomization.augment(this.builder.build(), null)
            .onItem().transform(id -> id.getRoles())
            .onItem().transform(roles -> {
                // Check that it have the correct role
                return roles.contains(Role.PROCESS_MANAGER) &&
                       roles.contains(Role.IMS_USER);
            })
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());

        subscriber
            .awaitItem()
            .assertItem(true);
    }

    @Test
    @DisplayName("CATALOG_MANAGER requires both VO and SLM group membership")
    public void testCatalogManager() {
        // Setup entitlements
        userInfo.addEntitlement(prefix + "role=member" + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=member", imsConfig.group()) + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), cm) + postfix);

        try {
            builder.addAttribute("userinfo", mapper.writeValueAsString(userInfo));
        } catch (JsonProcessingException e) {
            fail(e.getMessage());
        }

        // Parse roles from entitlements
        UniAssertSubscriber<Boolean> subscriber = this.roleCustomization.augment(this.builder.build(), null)
            .onItem().transform(id -> id.getRoles())
            .onItem().transform(roles -> {
                // Check that it have the correct role
                return roles.contains(Role.CATALOG_MANAGER) &&
                       roles.contains(Role.IMS_USER);
            })
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());

        subscriber
            .awaitItem()
            .assertItem(true);
    }

    @Test
    @DisplayName("REPORT_OWNER requires both VO and SLM group membership")
    public void testReportOwner() {
        // Setup entitlements
        userInfo.addEntitlement(prefix + "role=member" + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=member", imsConfig.group()) + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), ro) + postfix);

        try {
            builder.addAttribute("userinfo", mapper.writeValueAsString(userInfo));
        } catch (JsonProcessingException e) {
            fail(e.getMessage());
        }

        // Parse roles from entitlements
        UniAssertSubscriber<Boolean> subscriber = this.roleCustomization.augment(this.builder.build(), null)
            .onItem().transform(id -> id.getRoles())
            .onItem().transform(roles -> {
                // Check that it have the correct role
                return roles.contains(Role.REPORT_OWNER) &&
                       roles.contains(Role.IMS_USER);
            })
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());

        subscriber
            .awaitItem()
            .assertItem(true);
    }

    @Test
    @DisplayName("SLA_OWNER requires both VO and SLM group membership")
    public void testServiceLevelAgreementOwner() {
        // Setup entitlements
        userInfo.addEntitlement(prefix + "role=member" + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=member", imsConfig.group()) + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), slao) + postfix);

        try {
            builder.addAttribute("userinfo", mapper.writeValueAsString(userInfo));
        } catch (JsonProcessingException e) {
            fail(e.getMessage());
        }

        // Parse roles from entitlements
        UniAssertSubscriber<Boolean> subscriber = this.roleCustomization.augment(this.builder.build(), null)
            .onItem().transform(id -> id.getRoles())
            .onItem().transform(roles -> {
                // Check that it have the correct role
                return roles.contains(Role.SLA_OWNER) &&
                       roles.contains(Role.IMS_USER);
            })
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());

        subscriber
            .awaitItem()
            .assertItem(true);
    }

    @Test
    @DisplayName("OLA_OWNER requires both VO and SLM group membership")
    public void testOperationalLevelAgreementOwner() {
        // Setup entitlements
        userInfo.addEntitlement(prefix + "role=member" + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=member", imsConfig.group()) + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), olao) + postfix);

        try {
            builder.addAttribute("userinfo", mapper.writeValueAsString(userInfo));
        } catch (JsonProcessingException e) {
            fail(e.getMessage());
        }

        // Parse roles from entitlements
        UniAssertSubscriber<Boolean> subscriber = this.roleCustomization.augment(this.builder.build(), null)
            .onItem().transform(id -> id.getRoles())
            .onItem().transform(roles -> {
                // Check that it have the correct role
                return roles.contains(Role.OLA_OWNER) &&
                       roles.contains(Role.IMS_USER);
            })
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());

        subscriber
            .awaitItem()
            .assertItem(true);
    }

    @Test
    @DisplayName("UA_OWNER requires both VO and SLM group membership")
    public void testUnderpinningAgreementOwner() {
        // Setup entitlements
        userInfo.addEntitlement(prefix + "role=member" + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=member", imsConfig.group()) + postfix);
        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), uao) + postfix);

        try {
            builder.addAttribute("userinfo", mapper.writeValueAsString(userInfo));
        } catch (JsonProcessingException e) {
            fail(e.getMessage());
        }

        // Parse roles from entitlements
        UniAssertSubscriber<Boolean> subscriber = this.roleCustomization.augment(this.builder.build(), null)
            .onItem().transform(id -> id.getRoles())
            .onItem().transform(roles -> {
                // Check that it have the correct role
                return roles.contains(Role.UA_OWNER) &&
                       roles.contains(Role.IMS_USER);
            })
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create());

        subscriber
            .awaitItem()
            .assertItem(true);
    }
}
