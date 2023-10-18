package egi.checkin.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Details of a Check-in user
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckinUser {

    public final static String ATTR_USERID = "userID";
    public final static String ATTR_USERNAME = "userName";
    public final static String ATTR_FIRSTNAME = "firstName";
    public final static String ATTR_LASTNAME = "lastName";
    public final static String ATTR_FULLNAME = "fullName";
    public final static String ATTR_EMAIL = "email";
    public final static String ATTR_EMAILCHECKED = "emailVerified";
    public final static String ATTR_ASSURANCE = "assurance";

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("voperson_id")
    public String checkinUserId;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String fullName;

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
    @JsonProperty("preferred_user_name")
    public String userName;

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
    public CheckinUser() {}

    /***
     * Construct with Check-in user ID
     */
    public CheckinUser(String checkinUserId) { this.checkinUserId = checkinUserId; }

    /***
     * Construct from Check-in membership record
     */
    public CheckinUser(CheckinRole role) {

        this.checkinUserId = role.person.checkinUserId();

        // Get first complete name
        if(null != role.person.names)
            for(var name : role.person.names) {
                if(null != name.family && !name.family.isBlank() && null != name.given && !name.given.isBlank()) {
                    this.firstName = name.given;
                    this.lastName = name.family;
                    this.fullName = name.given + " " + name.family;
                    break;
                }
            }

        // Get first email address
        if(null != role.person.emails)
            for(var email : role.person.emails) {
                if(null != email.mail && !email.mail.isBlank()) {
                    this.email = email.mail;
                    this.emailIsVerified = email.verified;
                    break;
                }
            }
    }

    /***
     * Construct and return full name of the user
     * @return Full name of the user
     */
    public String getFullName() {
        if(null == this.fullName) {
            if(null != this.firstName)
                this.fullName = this.firstName;
            if(null != this.lastName) {
                if(null != this.fullName && !this.fullName.isBlank())
                    this.fullName += " ";
                this.fullName += this.lastName;
            }
        }

        return this.fullName;
    }

    public String getCheckinUserId() { return this.checkinUserId; }
    public CheckinUser setCheckinUserId(String userId) { this.checkinUserId = userId; return this; }
    public CheckinUser setFirstName(String firstName) { this.firstName = firstName; return this; }
    public CheckinUser setLastName(String lastName) { this.lastName = lastName; return this; }
    public CheckinUser setFullName(String fullName) { this.fullName = fullName; return this; }
    public CheckinUser setEmail(String email) { this.email = email; return this; }

    /***
     * Store another level of assurance (LoA)
     * @param loa The assurance
     * @return Ourselves, to allow chaining calls with .
     */
    public CheckinUser addAssurance(String loa) {
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
    public CheckinUser addEntitlement(String entitlement) {
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
    public CheckinUser addRole(String role) {
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
