package egi.checkin.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;


/**
 * Details of the current user
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserInfo extends BasicUserInfo {

    public final static String ATTR_USERID = "userID";
    public final static String ATTR_USERNAME = "userName";
    public final static String ATTR_FIRSTNAME = "firstName";
    public final static String ATTR_LASTNAME = "lastName";
    public final static String ATTR_EMAIL = "email";
    public final static String ATTR_EMAILCHECKED = "emailVerified";
    public final static String ATTR_ASSURANCE = "assurance";

    @Schema(enumeration={ "UserInfo" })
    public String kind = "UserInfo";

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("eduperson_assurance")
    public List<String> assurances;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("eduperson_entitlement")
    public List<String> entitlements;
}
