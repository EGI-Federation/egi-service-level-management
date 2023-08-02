package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.ArrayList;


/***
 * Map of roles, each with a list of users the role is assigned to
 */
public class UserRoles {

	@Schema(enumeration={ "UserRoles" })
	public String kind = "UserRoles";

	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	public List<Role> roles;


	/***
	 * Constructor
	 */
	public UserRoles() {}

	/***
	 * Store another role
	 * @param role The assigned role
	 */
	public UserRoles addRole(Role role) {
		if(null == this.roles)
			this.roles = new ArrayList<>();

		this.roles.add(role);

		return this;
	}
}
