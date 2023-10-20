package egi.eu.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.smallrye.common.constraint.NotNull;
import io.smallrye.mutiny.Uni;
import jakarta.persistence.*;

import java.util.List;

import egi.eu.model.User;


/**
 * Details of some user
 */
@Entity
@Cacheable
@Table(name = "users")
public class UserEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(unique = true, length = 120)
    @NotNull
    public String checkinUserId;

    public String fullName;

    public String email;


    /***
     * Constructor
     */
    public UserEntity() { super(); }

    /***
     * Copy constructor
     */
    public UserEntity(User user) {
        super();

        this.checkinUserId = user.checkinUserId;
        this.fullName = user.fullName;
        this.email = user.email;
    }

    /***
     * Get user with specified Id
     * @return User entity
     */
    public static Uni<UserEntity> findByCheckinUserId(String checkinUserId) {
        return find("checkinUserId", checkinUserId).firstResult();
    }

    /***
     * See which users already exist in the database
     * @return List with existing users
     */
    public static Uni<List<UserEntity>> findByCheckinUserIds(List<String> checkinUserIds) {
        return list("checkinUserId in ?1", checkinUserIds);
    }
}
