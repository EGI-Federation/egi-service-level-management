package egi.checkin.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;


/**
 * Details of the current user
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserInfo {

    public final static String ATTR_USERID = "userID";
    public final static String ATTR_USERNAME = "userName";
    public final static String ATTR_FIRSTNAME = "firstName";
    public final static String ATTR_LASTNAME = "lastName";
    public final static String ATTR_EMAIL = "email";
    public final static String ATTR_EMAILCHECKED = "emailVerified";
    public final static String ATTR_ASSURANCE = "assurance";

    public String kind = "UserInfo";

    @JsonProperty("voperson_id")
    public String userID;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("preferred_user_name")
    public String userName;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("given_name")
    public String firstName;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("family_name")
    public String lastName;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String email;

    @JsonProperty("email_verified")
    public boolean emailIsVerified;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("eduperson_assurance")
    public List<String> assurances;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("eduperson_entitlement")
    public List<String> entitlements;
}
