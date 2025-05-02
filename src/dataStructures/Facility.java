package dataStructures;

import java.io.Serializable;

import javafx.scene.control.Label;

public class Facility extends Label implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2935869521535935205L;
	private int id;
	private String name;
	private String description;
	private boolean deleted = false;
	private int organization_id;
	private String phone;
	private String fax;
	private String acronym;

	public Facility() {

	}

	public Facility(int id, String name, String description, boolean deleted, int organization_id, String phone, String acronym, String fax) {
		super();
		this.id = id;
		this.name = name;
		this.description = description;
		this.deleted = deleted;
		this.organization_id = organization_id;
		this.phone = phone;
		this.acronym = acronym;
		this.fax = fax;
		this.setText(name);
	}

	public int get_id() {
		return id;
	}

	public void set_id(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.setText(name);
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

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getAcronym() {
		if (acronym != null)
			return acronym;
		else
			return "";
	}

	public void setAcronym(String acronym) {
		this.acronym = acronym;
	}

	@Override
	public String toString() {
		return name;
	}

	public String getFax() {
		return fax;
	}

	public void setFax(String fax) {
		this.fax = fax;
	}

}
