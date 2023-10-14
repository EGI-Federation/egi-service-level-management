package egi.eu;

import egi.checkin.SlmMockCheckin;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.inject.Inject;

import egi.checkin.Checkin;
import egi.checkin.SlmMockCheckinProxy;


@QuarkusTest
@QuarkusTestResource(SlmMockCheckinProxy.class)
public class SlmRoleManipulationTest {

    @Inject
    IntegratedManagementSystemConfig imsConfig;

    private SlmMockCheckin mockCheckin;

    private static final Logger log = Logger.getLogger(SlmRoleManipulationTest.class);
    private ObjectMapper mapper = new ObjectMapper();
    private Checkin checkin;


    @BeforeEach
    public void setupEach() {
        this.checkin = new Checkin();
        var test = imsConfig.group();
        int i = 19;
    }

    @Test
    @DisplayName("Add user to group adds new membership record")
    public void testAddUserToGroup() {
        // Setup entitlements
//        userInfo.addEntitlement(prefix + "admins:role=member" + postfix);
//        userInfo.addEntitlement(prefix + String.format("%s:role=member", imsConfig.group()) + postfix);
//        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), po) + postfix);
//        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), pm) + postfix);
//        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), pd) + postfix);
//        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), co) + postfix);
//        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), ro) + postfix);
//        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), uao) + postfix);
//        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), olao) + postfix);
//        userInfo.addEntitlement(prefix + String.format("%s:role=%s", imsConfig.group(), slao) + postfix);
//
//        try {
//            builder.addAttribute("userinfo", mapper.writeValueAsString(userInfo));
//        } catch (JsonProcessingException e) {
//            fail(e.getMessage());
//        }
//
//        // Parse roles from entitlements
//        UniAssertSubscriber<Boolean> subscriber = this.roleCustomization.augment(this.builder.build(), null)
//            .onItem().transform(id -> id.getRoles())
//            .onItem().transform(roles -> {
//                // Check that it does not have any of the roles
//                return roles.contains(Role.IMS_USER) ||
//                       roles.contains(Role.IMS_ADMIN) ||
//                       roles.contains(Role.PROCESS_OWNER) ||
//                       roles.contains(Role.PROCESS_MANAGER) ||
//                       roles.contains(Role.PROCESS_DEVELOPER) ||
//                       roles.contains(Role.CATALOG_OWNER) ||
//                       roles.contains(Role.REPORT_OWNER) ||
//                       roles.contains(Role.UA_OWNER) ||
//                       roles.contains(Role.OLA_OWNER) ||
//                       roles.contains(Role.SLA_OWNER);
//            })
//            .subscribe()
//            .withSubscriber(UniAssertSubscriber.create());
//
//        subscriber
//            .awaitItem()
//            .assertItem(false);
    }
}
