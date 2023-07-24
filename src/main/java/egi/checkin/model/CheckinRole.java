package egi.checkin.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

    /***
     * The Id of the membership record
     * You will have to refer to this when updating or deleting this membership/role
     */
    @JsonProperty("Id")
    public int roleId;

    /***
     * The group of VO this role is about
     */
    @JsonProperty("CouId")
    public int groupId;

    /***
     * The user this role is about
     */
    @JsonProperty("Person")
    public Person person;

    /***
     * The role of the user
     * Will be "member" to show membership in the group or VO, or the role name
     */
    public String role;

    @JsonProperty("Affiliation")
    public String affiliation;

    @JsonProperty("Title")
    public String title;

    @JsonProperty("Status")
    public String status;

    @JsonProperty("ValidFrom")
    @JsonFormat(pattern="yyyy-MM-dd' 'HH:mm:ss")
    public Date from;

    @JsonProperty("ValidThrough")
    @JsonFormat(pattern="yyyy-MM-dd' 'HH:mm:ss")
    public Date until;

    @JsonProperty("Created")
    @JsonFormat(pattern="yyyy-MM-dd' 'HH:mm:ss")
    public Date created;

    @JsonProperty("Modified")
    @JsonFormat(pattern="yyyy-MM-dd' 'HH:mm:ss")
    public Date modified;

    @JsonProperty("ActorIdentifier")
    public String modifiedBy;

    @JsonProperty("Version")
    public String version;

    @JsonProperty("Revision")
    public int revision;

    @JsonProperty("Deleted")
    public boolean deleted;


    /***
     * Details of the user
     */
    public static class Person {

        public int Id;

        @JsonProperty("Type")
        public String type;

        @JsonProperty("Identifier")
        public List<Identifier> ids;

        @JsonProperty("Name")
        public List<Name> names;

        @JsonProperty("EmailAddress")
        public List<Email> emails;


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
}
