package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import egi.checkin.model.CheckinUser;
import egi.eu.entity.UserEntity;

import java.util.Set;


/**
 * Details of some user
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class User {

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Long checkinUserId = null;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String fullName;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String email;

    /***
     * Constructor
     */
    public User() {}

    /***
     * Copy constructor
     */
    public User(UserEntity u) {
        super();

        if(null != u) {
            this.checkinUserId = u.checkinUserId;
            this.fullName = u.fullName;
            this.email = u.email;
        }
    }

    /***
     * Copy constructor
     */
    public User(CheckinUser u) {
        super();

        if(null != u) {
            this.checkinUserId = u.checkinUserId;
            this.fullName = u.fullName;
            this.email = u.email;
        }
    }

    /***
     * Check if a valid Check-in identity is wrapped by this object
     * @return True if valid Check-in identity
     */
    @JsonIgnore
    public boolean isValid() { return null != this.checkinUserId; }
}
