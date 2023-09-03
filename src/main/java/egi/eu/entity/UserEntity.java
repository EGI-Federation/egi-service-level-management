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

    @Column(unique = true)
    @NotNull
    public Long checkinUserId;

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
     * See which users already exist in the database
     * @return List with existing users
     */
    public static Uni<List<UserEntity>> findUsersWithCheckinUserIds(List<Long> checkinUserIds) {
        return list("checkinUserId in ?1", checkinUserIds);
    }

    /***
     * See which users already exist in the database
     * @return List with existing users
     */
    public static Uni<UserEntity> findByCheckinUserId(Long checkinUserId) {
        return find("checkinUserId", checkinUserId).firstResult();
    }
}
