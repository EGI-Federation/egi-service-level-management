package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import egi.checkin.model.CheckinUser;
import egi.eu.entity.UserEntity;


/**
 * Details of some user
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class User {

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Long checkinUserId;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String fullName;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String email;


    /***
     * Constructor
     */
    public User() { super(); }

    /***
     * Copy constructor
     */
    protected User(UserEntity u) {
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
    protected User(CheckinUser u) {
        super();

        if(null != u) {
            this.checkinUserId = u.checkinUserId;
            this.fullName = u.fullName;
            this.email = u.email;
        }
    }
}
