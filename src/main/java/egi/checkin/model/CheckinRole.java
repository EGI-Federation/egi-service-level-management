package egi.checkin.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.List;


/**
 * Details of a membership record
 * Such a record can represent membership in a group or VO (role field is "member")
 * or a role (role field is the name of the role)
 *
 * Note: Multiple records can exist for the same membership/role, e.g. with different
 *       start/until dates and different statuses.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckinRole {

    @JsonProperty("Version")
    public String version = "1.0";

    /***
     * The Id of the membership record
     * You will have to refer to this when updating or deleting this membership/role
     */
    @JsonProperty("Id")
    public long roleId;

    /***
     * The user this role is about
     */
    @JsonProperty("Person")
    public Person person;

    /***
     * The group or VO this role is about
     */
    @JsonProperty("CouId")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public long groupId;

    /***
     * The group or VO this role is about
     */
    @JsonProperty("Cou")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Group group;

    /***
     * The role of the user
     * Will be "member" to show membership in the group or VO, or the role name
     */
    public String role;

    @JsonProperty("Affiliation")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String affiliation;

    @JsonProperty("Title")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String title;

    @JsonProperty("Status")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String status;

    @JsonProperty("ValidFrom")
    @JsonFormat(pattern="yyyy-MM-dd' 'HH:mm:ss")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date from;

    @JsonProperty("ValidThrough")
    @JsonFormat(pattern="yyyy-MM-dd' 'HH:mm:ss")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date until;

    @JsonProperty("Created")
    @JsonFormat(pattern="yyyy-MM-dd' 'HH:mm:ss")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date created;

    @JsonProperty("Modified")
    @JsonFormat(pattern="yyyy-MM-dd' 'HH:mm:ss")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date modified;

    @JsonProperty("ActorIdentifier")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String modifiedBy;

    @JsonProperty("Revision")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int revision;

    @JsonProperty("Deleted")
    public boolean deleted;


    /***
     * Constructor
     */
    public CheckinRole() {}

    /***
     * Construct role record for specified user
     */
    public CheckinRole(long checkinUserId, String groupName, String coId, String affiliation, String status) {
        this.person = new Person(checkinUserId);
        this.group = new Group(coId, groupName);
        this.affiliation = affiliation;
        this.status = status;
        this.deleted = false;
    }

    /***
     * Check if this is a membership record or a role record
     */
    public boolean isRole() {
        return null != this.title && !this.title.isBlank() && this.title.equalsIgnoreCase(this.role);
    }


    /***
     * Details of the user
     */
    public static class Person {

        public long Id;

        @JsonProperty("Type")
        public String type;

        @JsonProperty("Identifier")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public List<Identifier> ids;

        @JsonProperty("Name")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public List<Name> names;

        @JsonProperty("EmailAddress")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public List<Email> emails;


        /***
         * Constructor
         */
        public Person() {}

        /***
         * Construct as specific user
         */
        public Person(long checkinUserId) {
            this.type = "CO";
            this.Id = checkinUserId;
        }


        /***
         * An identifier the user is known by
         */
        public static class Identifier {
            public String type;
            public String identifier;
        }

        /***
         * A name the user is known by
         */
        public static class Name {
            public String type;
            public String given;
            public String middle;
            public String family;
        }

        /***
         * An email address of the user
         */
        public static class Email {
            public String type;
            public String mail;
            public boolean verified;
        }

    }

    /***
     * Details of the VO or group
     */
    public static class Group {

        @JsonProperty("CoId")
        public String coId;

        @JsonProperty("Name")
        public String name;


        /***
         * Constructor
         */
        public Group() {
        }

        /***
         * Construct as specific group
         */
        public Group(String coId, String name) {
            this.coId = coId;
            this.name = name;
        }
    }
}
