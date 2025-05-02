package dataStructures;

import java.io.Serializable;

import javafx.scene.control.Label;

public class Provider extends Label implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7748369371175945964L;
	private int _id;
	private String name;
	private String description;
	private boolean deleted = false;
	private int organization_id;

	public Provider() {

	}

	public Provider(int _id, String name, String description, boolean deleted, int organization_id) {
		super();
		this._id = _id;
		this.name = name;
		this.description = description;
		this.deleted = deleted;
		this.organization_id = organization_id;
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
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public int getOrganization_id() {
		return organization_id;
	}

	public void setOrganization_id(int organization_id) {
		this.organization_id = organization_id;
	}

	@Override
	public String toString() {
		return name;
	}

}
