package egi.eu;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import egi.eu.model.Role;
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

import egi.checkin.model.CheckinUser;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;


/***
 * Class to customize role identification from the user information
 * See also https://quarkus.io/guides/security-customization#security-identity-customization
 */
@ApplicationScoped
public class SlmRoleCustomization implements SecurityIdentityAugmentor {

    private static final Logger log = Logger.getLogger(SlmRoleCustomization.class);

    @Inject
    protected IntegratedManagementSystemConfig config;

    public void setConfig(IntegratedManagementSystemConfig config) {
        this.config = config;
    }

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

            log.debug("Building security identity");

            // Extract the OIDC user information, loaded due to the setting quarkus.roles.source=userinfo
            var ui = identity.getAttribute("userinfo");
            var isAJO = ui instanceof AbstractJsonObjectResponse;
            if(null != ui && (isAJO || ui instanceof String)) {
                // Construct Check-in UserInfo from the user info fetched by OIDC
                CheckinUser userInfo = null;
                String json = null;
                try {
                    var mapper = new ObjectMapper();
                    json = isAJO ? ((AbstractJsonObjectResponse)ui).getJsonObject().toString() : ui.toString();
                    userInfo = mapper.readValue(json, CheckinUser.class);

                    if(null != userInfo.checkinUserId)
                        builder.addAttribute(CheckinUser.ATTR_USERID, userInfo.checkinUserId);

                    if(null != userInfo.userName)
                        builder.addAttribute(CheckinUser.ATTR_USERNAME, userInfo.userName);

                    if(null != userInfo.firstName)
                        builder.addAttribute(CheckinUser.ATTR_FIRSTNAME, userInfo.firstName);

                    if(null != userInfo.lastName)
                        builder.addAttribute(CheckinUser.ATTR_LASTNAME, userInfo.lastName);

                    if(null != userInfo.fullName || null != userInfo.firstName || null != userInfo.lastName)
                        builder.addAttribute(CheckinUser.ATTR_FULLNAME, userInfo.getFullName());

                    if(null != userInfo.email)
                        builder.addAttribute(CheckinUser.ATTR_EMAIL, userInfo.email);

                    builder.addAttribute(CheckinUser.ATTR_EMAILCHECKED, userInfo.emailIsVerified);

                    if(null != userInfo.assurances) {
                        Pattern assuranceRex = Pattern.compile("^https?\\://(aai[^\\.]*.egi.eu)/LoA#([^\\:#/]+)");
                        for(var a : userInfo.assurances) {
                            var matcher = assuranceRex.matcher(a);
                            if(matcher.matches()) {
                                // Got an EGI Check-in backed assurance level
                                var assurance = matcher.group(2);
                                builder.addAttribute(CheckinUser.ATTR_ASSURANCE, assurance.toLowerCase());
                                break;
                            }
                        }
                    }
                }
                catch (JsonProcessingException e) {
                    // Error deserializing JSON info UserInfo instance
                    MDC.put("OIDC.userinfo", null != json ? json : "null");
                    log.warn("Cannot deserialize OIDC userinfo");
                }

                if(null != userInfo) {
                    // Got the Check-in user information, map roles
                    var roleNames = config.roles();

                    final String voPrefix = "urn:mace:egi.eu:group:" + config.vo().toLowerCase() + ":";
                    final String suffix = "#aai.egi.eu";

                    // Only continue checking the roles for members of the configured VO
                    if(userInfo.entitlements.contains(voPrefix + "role=member" + suffix)) {
                        // This user is member of the VO, access to ISM tools is allowed
                        builder.addRole(Role.IMS_USER);

                        final String imsPrefix = voPrefix + "ims:role=";
                        final String imsOwner = imsPrefix + "ims-owner" + suffix;
                        final String imsManager = imsPrefix + "ims-manager" + suffix;
                        final String rolePrefix = voPrefix + config.group() + ":role=";

                        boolean systemMember = false;
                        boolean processMember = false;
                        if(userInfo.entitlements.contains(rolePrefix + "member" + suffix)) {
                            // This user is member of the SLM group, which is a prerequisite to holding SLM roles
                            processMember = true;
                            builder.addRole(Role.PROCESS_MEMBER);
                        }
                        if(userInfo.entitlements.contains(imsPrefix + "member" + suffix)) {
                            // This user is member of the IMS group, which is a prerequisite to holding admin roles
                            systemMember = true;
                        }

                        final String rexPrefix = "^urn\\:mace\\:egi.eu\\:group\\:" +
                                config.vo().toLowerCase() + "\\:" +
                                config.group() + "\\:role=";

                        final String po = rolePrefix + roleNames.get("process-owner").toLowerCase() + suffix;
                        final String pm = rolePrefix + roleNames.get("process-manager").toLowerCase() + suffix;
                        final String pd = rolePrefix + roleNames.get("process-developer").toLowerCase() + suffix;
                        final String co = rolePrefix + roleNames.get("catalog-owner").toLowerCase() + suffix;
                        final String ro = rolePrefix + roleNames.get("report-owner").toLowerCase() + suffix;
                        final String uao = rolePrefix + roleNames.get("ua-owner").toLowerCase() + suffix;
                        final String olao = rolePrefix + roleNames.get("ola-owner").toLowerCase() + suffix;
                        final String slao = rolePrefix + roleNames.get("sla-owner").toLowerCase() + suffix;

                        final String roRex = rexPrefix + ro.replace("-", "\\-") + "(\\-[^\\#]+)";
                        final String uaoRex = rexPrefix + uao.replace("-", "\\-") + "(\\-[^\\#]+)";
                        final String olaoRex = rexPrefix + olao.replace("-", "\\-") + "(\\-[^\\#]+)";
                        final String slaoRex = rexPrefix + slao.replace("-", "\\-") + "(\\-[^\\#]+)";

                        Pattern pro = Pattern.compile(roRex);
                        Pattern puao = Pattern.compile(uaoRex);
                        Pattern polao = Pattern.compile(olaoRex);
                        Pattern pslao = Pattern.compile(slaoRex);

                        for (var e : userInfo.entitlements) {

                            if (systemMember && (e.equals(imsOwner) || e.equals(imsManager)))
                                builder.addRole(Role.IMS_ADMIN);
                            else if (processMember && e.equals(po))
                                builder.addRole(Role.PROCESS_OWNER);
                            else if (processMember && e.equals(pm))
                                builder.addRole(Role.PROCESS_MANAGER);
                            else if (processMember && e.equals(pd))
                                builder.addRole(Role.PROCESS_DEVELOPER);
                            else if (processMember && e.equals(co))
                                builder.addRole(Role.CATALOG_OWNER);
                            else if (processMember && e.equals(ro))
                                builder.addRole(Role.REPORT_OWNER);
                            else if (processMember && e.equals(uao))
                                builder.addRole(Role.UA_OWNER);
                            else if (processMember && e.equals(olao))
                                builder.addRole(Role.OLA_OWNER);
                            else if (processMember && e.equals(slao))
                                builder.addRole(Role.SLA_OWNER);
                            else if (processMember) {

                                Matcher m = pro.matcher(e);
                                if (m.find()) {
                                    // The user is the owner of a specific report
                                    var roRole = Role.REPORT_OWNER + m.group(1);
                                    builder.addRole(roRole);
                                    continue;
                                }

                                m = puao.matcher(e);
                                if (m.find()) {
                                    // The user is the owner of a specific UA
                                    var uaoRole = Role.UA_OWNER + m.group(1);
                                    builder.addRole(uaoRole);
                                    continue;
                                }

                                m = polao.matcher(e);
                                if (m.find()) {
                                    // The user is the owner of a specific OLA
                                    var olaoRole = Role.OLA_OWNER + m.group(1);
                                    builder.addRole(olaoRole);
                                    continue;
                                }

                                m = pslao.matcher(e);
                                if (m.find()) {
                                    // The user is the owner of a specific SLA
                                    var slaoRole = Role.SLA_OWNER + m.group(1);
                                    builder.addRole(slaoRole);
                                }
                            }
                        }
                    } // IMS_USER
                }
            }

            return builder::build;
        }
    }
}
