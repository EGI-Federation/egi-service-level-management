package egi.eu.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;


/**
 * Details of some user
 */
@Entity
@Table(name = "users")
public class UserEntity extends PanacheEntity {

    @Column(unique = true)
    public Long checkinUserId;

    public String fullName;

    public String email;


    /***
     * Constructor
     */
    public UserEntity() { super(); }
}
