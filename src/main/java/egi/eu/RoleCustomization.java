package egi.eu;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.oidc.runtime.AbstractJsonObjectResponse;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import egi.checkin.model.UserInfo;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;


/***
 * Class to customize role identification from the user information
 * See also https://quarkus.io/guides/security-customization#security-identity-customization
 */
@ApplicationScoped
public class RoleCustomization implements SecurityIdentityAugmentor {

    private static final Logger log = Logger.getLogger(RoleCustomization.class);

    @Inject
    protected IntegratedManagementSystemConfig config;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        // NOTE: In case role parsing is a blocking operation, replace with the line below
        // return context.runBlocking(this.build(identity));
        return Uni.createFrom().item(this.build(identity));
    }

    private Supplier<SecurityIdentity> build(SecurityIdentity identity) {
        if(identity.isAnonymous()) {
            return () -> identity;
        } else {
            // Create a new builder and copy principal, attributes, credentials and roles from the original identity
            QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);

            // Extract the OIDC user information, loaded due to the setting quarkus.roles.source=userinfo
            var ui = identity.getAttribute("userinfo");
            if(null != ui && ui instanceof AbstractJsonObjectResponse) {
                // Construct Check-in UserInfo from the user info fetched by OIDC
                UserInfo userInfo = null;
                String json = null;
                try {
                    var mapper = new ObjectMapper();
                    json = ((AbstractJsonObjectResponse)ui).getJsonObject().toString();
                    userInfo = mapper.readValue(json, UserInfo.class);

                    if(null != userInfo.userID)
                        builder.addAttribute(UserInfo.ATTR_USERID, userInfo.userID);

                    if(null != userInfo.userName)
                        builder.addAttribute(UserInfo.ATTR_USERNAME, userInfo.userName);

                    if(null != userInfo.firstName)
                        builder.addAttribute(UserInfo.ATTR_FIRSTNAME, userInfo.firstName);

                    if(null != userInfo.lastName)
                        builder.addAttribute(UserInfo.ATTR_LASTNAME, userInfo.lastName);

                    if(null != userInfo.email)
                        builder.addAttribute(UserInfo.ATTR_EMAIL, userInfo.email);

                    builder.addAttribute(UserInfo.ATTR_EMAILCHECKED, userInfo.emailIsVerified);

                    if(null != userInfo.assurances) {
                        Pattern assuranceRex = Pattern.compile("^https?\\://(aai[^\\.]*.egi.eu)/LoA#([^\\:#/]+)");
                        for(var a : userInfo.assurances) {
                            var matcher = assuranceRex.matcher(a);
                            if(matcher.matches()) {
                                // Got an EGI Check-in backed assurance level
                                var assurance = matcher.group(2);
                                builder.addAttribute(UserInfo.ATTR_ASSURANCE, assurance.toLowerCase());
                                break;
                            }
                        }
                    }
                }
                catch (JsonProcessingException e) {
                    // Error deserializing JSON info UserInfo instance
                    MDC.put("OIDC.userinfo", json);
                    log.warn("Cannot deserialize OIDC userinfo");
                }

                if(null != userInfo) {
                    // Got the Check-in user information, map roles
                    var roleNames = config.roles();

                    final String voPrefix = "urn:mace:egi.eu:group:" + config.vo().toLowerCase() + ":";
                    final String rexPrefix = "^urn\\:mace\\:egi.eu\\:group\\:" + config.vo().toLowerCase() + "\\:role=";
                    final String suffix = "#aai.egi.eu";

                    if(userInfo.entitlements.contains(voPrefix + "role=member" + suffix)) {
                        // This user is member of the VO, access to ISM tools is allowed
                        builder.addRole(Role.ISM_USER);

                        // Only continue checking the rest of the roles for VO members
                        final String rolePrefix = voPrefix + "role=";
                        final String voManager = voPrefix + "admins:role=member" + suffix;

                        final String po = roleNames.get("process-owner").toLowerCase();
                        final String pm = roleNames.get("process-manager").toLowerCase();
                        final String cm = roleNames.get("catalog-manager").toLowerCase();
                        final String ro = roleNames.get("report-owner").toLowerCase();
                        final String uao = roleNames.get("ua-owner").toLowerCase();
                        final String olao = roleNames.get("ola-owner").toLowerCase();
                        final String slao = roleNames.get("sla-owner").toLowerCase();

                        final String roRex = rexPrefix + ro.replace("-", "\\-") + "(\\-[^\\#]+)";
                        final String uaoRex = rexPrefix + uao.replace("-", "\\-") + "(\\-[^\\#]+)";
                        final String olaoRex = rexPrefix + olao.replace("-", "\\-") + "(\\-[^\\#]+)";
                        final String slaoRex = rexPrefix + slao.replace("-", "\\-") + "(\\-[^\\#]+)";

                        Pattern pro = Pattern.compile(roRex);
                        Pattern puao = Pattern.compile(uaoRex);
                        Pattern polao = Pattern.compile(olaoRex);
                        Pattern pslao = Pattern.compile(slaoRex);

                        for(var e : userInfo.entitlements) {

                            if(e.equals(voManager))
                                builder.addRole(Role.ISM_ADMIN);
                            else if(e.equals(rolePrefix + po + suffix))
                                builder.addRole(Role.PROCESS_OWNER);
                            else if(e.equals(rolePrefix + pm + suffix))
                                builder.addRole(Role.PROCESS_MANAGER);
                            else if(e.equals(rolePrefix + cm + suffix))
                                builder.addRole(Role.CATALOG_MANAGER);
                            else if(e.equals(rolePrefix + ro + suffix))
                                builder.addRole(Role.REPORT_OWNER);
                            else if(e.equals(rolePrefix + uao + suffix))
                                builder.addRole(Role.UA_OWNER);
                            else if(e.equals(rolePrefix + olao + suffix))
                                builder.addRole(Role.OLA_OWNER);
                            else if(e.equals(rolePrefix + slao + suffix))
                                builder.addRole(Role.SLA_OWNER);
                            else {

                                Matcher m = pro.matcher(e);
                                if(m.find()) {
                                    // The user is the owner of a specific report
                                    var roRole = Role.REPORT_OWNER + m.group(1);
                                    builder.addRole(roRole);
                                    continue;
                                }

                                m = puao.matcher(e);
                                if(m.find()) {
                                    // The user is the owner of a specific UA
                                    var uaoRole = Role.UA_OWNER + m.group(1);
                                    builder.addRole(uaoRole);
                                    continue;
                                }

                                m = polao.matcher(e);
                                if(m.find()) {
                                    // The user is the owner of a specific OLA
                                    var olaoRole = Role.OLA_OWNER + m.group(1);
                                    builder.addRole(olaoRole);
                                    continue;
                                }

                                m = pslao.matcher(e);
                                if(m.find()) {
                                    // The user is the owner of a specific SLA
                                    var slaoRole = Role.SLA_OWNER + m.group(1);
                                    builder.addRole(slaoRole);
                                }
                            }
                        }
                    }
                }
            }

            return builder::build;
        }
    }
}
