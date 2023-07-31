package egi.eu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

import egi.checkin.model.UserInfo;


/***
 * List of users
 */
public class UserList {

	@Schema(enumeration={ "UserList" })
	public String kind = "UserList";

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public List<UserInfo> users;


	/***
	 * Constructor
	 */
	public UserList() {}

	/***
	 * Copy constructor
	 * @param users List of users to copy
	 */
	public UserList(List<UserInfo> users) {
		this.users = new ArrayList<>(users.size());
		this.users.addAll(users);
	}
}