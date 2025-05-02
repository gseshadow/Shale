package dataStructures;

import java.io.Serializable;
import java.util.ArrayList;
import javafx.scene.control.Label;

public class Organization extends Label implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5924087356170946597L;
	private int _id;
	private String name;
	private String description;
	private boolean is_deleted;
	private int creator_user_id;
	private ArrayList<User> users;
	private ArrayList<Integer> user_ids = new ArrayList<>();

	public Organization() {
	}

	public Organization(int _id, String name, String description, boolean is_deleted, int creator_user_id) {
		super();
		this._id = _id;
		this.name = name;
		this.description = description;
		this.is_deleted = is_deleted;
		this.creator_user_id = creator_user_id;
		this.setText(name);
	}

	public Organization(String name) {
		this.name = name;
		this.setText(name);
	}

	public int get_id() {
		return _id;
	}

	public void set_id(int _id) {
		this._id = _id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
		this.setText(name);
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean isIs_deleted() {
		return is_deleted;
	}

	public void setIs_deleted(boolean is_deleted) {
		this.is_deleted = is_deleted;
	}

	public int getCreator_user_id() {
		return creator_user_id;
	}

	public void setCreator_user_id(int creator_user_id) {
		this.creator_user_id = creator_user_id;
	}

	public ArrayList<User> getUsers() {
		return users;
	}

	public void setUsers(ArrayList<User> users) {
		this.users = users;
	}

	public ArrayList<Integer> getUser_ids() {
		return user_ids;
	}

	public void setUser_ids(ArrayList<Integer> user_ids) {
		this.user_ids = user_ids;
	}

	@Override
	public String toString() {
		return name;

	}

}
