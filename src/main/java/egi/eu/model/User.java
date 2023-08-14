package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import egi.checkin.model.CheckinUser;


/**
 * Details of some user
 */
@Entity
@Table(name = "users")
@JsonIgnoreProperties(ignoreUnknown = true)
public class User extends PanacheEntity {

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
    protected User(CheckinUser u) {
        super();

        this.checkinUserId = u.checkinUserId;
        this.fullName = u.fullName;
        this.email = u.email;
    }
}
