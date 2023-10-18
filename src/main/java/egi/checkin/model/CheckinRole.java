package egi.checkin.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.IOException;
import java.util.ArrayList;
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
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public long roleId;

    /***
     * The user this role is about
     */
    @JsonProperty("Person")
    @JsonInclude(JsonInclude.Include.NON_NULL)
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Group group;

    /***
     * The role of the user
     * Will be "member" to show membership in the group or VO, or the role name
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
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
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Boolean deleted;


    /***
     * Constructor
     */
    public CheckinRole() {}

    /***
     * Construct role record for specified user
     */
    public CheckinRole(String checkinUserId, String groupName, String coId, String affiliation, String status) {
        this.person = new Person(checkinUserId);
        this.group = new Group(coId, groupName);
        this.affiliation = affiliation;
        this.status = status;
        this.deleted = false;
    }

    /***
     * Check if this is a membership record or a role record
     */
    public boolean checkIfRole() {
        return null != this.title && !this.title.isBlank() && this.title.equalsIgnoreCase(this.role);
    }


    /***
     * Details of the user
     */
    public static class Person {

        @JsonProperty("Type")
        public String type = "CO";

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        public long Id; // CoManage Id

        @JsonProperty("Identifier")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonSerialize(using = IdentifierListSerializer.class)
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
        public Person(String checkinUserId) {
            this.ids = new ArrayList<>();
            this.ids.add(new Identifier(checkinUserId));
        }

        /***
         * Construct as specific user
         */
        public Person(long coManageId) {
            this.Id = coManageId;
        }

        /***
         * Get the Check-in user Id.
         * @return Check-in user Id, null if no identifier of type "epuid" found
         */
        public String checkinUserId() {
            if(null != this.ids && !this.ids.isEmpty())
                for(var id : this.ids) {
                    if(null != id.type && id.type.equalsIgnoreCase("epuid")) {
                        if(null != id.id)
                            return id.id;
                        if(null != id.identifier)
                            return id.identifier;
                    }
                }

            return null;
        }

        /***
         * Custom serializer for field ids. Serialize it as a single object when it contains just one Id.
         */
        public static class IdentifierListSerializer extends JsonSerializer<List<Identifier>> {

            @Override
            public void serialize(List<Identifier> ids, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
                    throws IOException, JsonProcessingException {

                if(ids.size() > 1)
                    jsonGenerator.writeStartArray();

                for(var id : ids)
                    jsonGenerator.writeObject(id);

                if(ids.size() > 1)
                    jsonGenerator.writeEndArray();
            }
        }


        /***
         * An identifier the user is known by
         */
        public static class Identifier {

            @JsonProperty("Type")
            public String kind;

            @JsonProperty("Id")
            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            public String id;

            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            public String type;

            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            public String identifier;

            /***
             * Constructor
             */
            public Identifier() {}

            /***
             * Construct as specific user
             */
            public Identifier(String checkinUserId) {
                this.kind = "epuid";
                this.id = checkinUserId;
            }
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
