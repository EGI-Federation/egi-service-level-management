package egi.checkin.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


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

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Set<String> roles;


    /***
     * Constructor
     */
    public UserInfo() {}

    /***
     * Construct with Check-in user ID
     */
    public UserInfo(int checkinUserId) { this.checkinUserId = checkinUserId; }

    /***
     * Construct from Check-in membership record
     */
    public UserInfo(CheckinRole role) { super(role); }

    public long getCheckinUserId() { return this.checkinUserId; }
    public UserInfo setUserId(String userId) { this.userId = userId; return this; }
    public UserInfo setCheckinUserId(long userId) { this.checkinUserId = userId; return this; }
    public UserInfo setFirstName(String firstName) { this.firstName = firstName; return this; }
    public UserInfo setLastName(String lastName) { this.lastName = lastName; return this; }
    public UserInfo setFullName(String fullName) { this.fullName = fullName; return this; }
    public UserInfo setEmail(String email) { this.email = email; return this; }

    /***
     * Store another level of assurance (LoA)
     * @param loa The assurance
     * @return Ourselves, to allow chaining calls with .
     */
    public UserInfo addAssurance(String loa) {
        if(null == this.assurances)
            this.assurances = new ArrayList<>();

        this.assurances.add(loa);

        return this;
    }

    /***
     * Store another entitlement
     * @param entitlement The entitlement
     * @return Ourselves, to allow chaining calls with .
     */
    public UserInfo addEntitlement(String entitlement) {
        if(null == this.entitlements)
            this.entitlements = new ArrayList<>();

        this.entitlements.add(entitlement);

        return this;
    }

    /***
     * Store another role
     * @param role The role
     * @return Ourselves, to allow chaining calls with .
     */
    public UserInfo addRole(String role) {
        if(null == this.roles)
            this.roles = new HashSet<>();

        this.roles.add(role);

        return this;
    }

    /***
     * Serialize ourselves to JSON
     * @return JSON string, empty string on error
     */
    public String toJsonString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {}

        return "";
    }
}
