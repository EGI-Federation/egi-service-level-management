package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import egi.checkin.model.CheckinUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/**
 * Details of the current user
 * Used only be the endpoint GET /user/info
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserInfo extends User {

    @Schema(enumeration={ "UserInfo" })
    public String kind = "UserInfo";

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("given_name")
    public String firstName;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("family_name")
    public String lastName;

    @JsonProperty("email_verified")
    public Boolean emailIsVerified;

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
     * Copy constructor
     */
    public UserInfo(CheckinUser u) {
        super(u);

        if(null != u) {
            this.firstName = u.firstName;
            this.lastName = u.lastName;
            this.emailIsVerified = u.emailIsVerified;
            this.roles  = u.roles;

            if (null != u.assurances) {
                this.assurances = new ArrayList<>(u.assurances.size());
                this.assurances.addAll(u.assurances);
            }

            if (null != u.entitlements) {
                this.entitlements = new ArrayList<>(u.entitlements.size());
                this.entitlements.addAll(u.entitlements);
            }
        }
    }
}
