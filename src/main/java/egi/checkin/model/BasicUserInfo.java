package egi.checkin.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Details of an user
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BasicUserInfo {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("voperson_id")
    public String userId;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public long checkinUserId;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("preferred_user_name")
    public String userName;

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


    /***
     * Constructor
     */
    public BasicUserInfo() {}

    /***
     * Construct from Check-in membership record
     */
    public BasicUserInfo(CheckinRole role) {

        this.checkinUserId = role.person.Id;

        // Get first voperson_id
        for(var id : role.person.ids) {
            if(null != id.type && id.type.equals("epuid")) {
                this.userId = id.identifier;
                break;
            }
        }

        // Get first complete name
        for(var name : role.person.names) {
            if(null != name.family && !name.family.isBlank() && null != name.given && !name.given.isBlank()) {
                this.firstName = name.given;
                this.lastName = name.family;
                this.fullName = name.given + " " + name.family;
                break;
            }
        }

        // Get first email address
        for(var email : role.person.emails) {
            if(null != email.mail && !email.mail.isBlank()) {
                this.email = email.mail;
                this.emailIsVerified = email.verified;
                break;
            }
        }
    }

}
