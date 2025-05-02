package dataStructures;

import java.io.Serializable;

import javafx.scene.control.Label;

public class PracticeArea extends Label implements Serializable {

	/**
	 * 1 = Medical Malpractice 2 = Personal Injury 3 = Sexual Assault
	 * 
	 */
	private static final long serialVersionUID = -1359627472733762775L;
	private int _id;
	private String practice_area = "";
	private boolean is_deleted;
	private int organization_id;
	private String organizationName = "";

	public PracticeArea() {

	}

	public PracticeArea(int _id, String practice_area, boolean is_deleted, int organization_id, String organizationName) {
		super();
		this._id = _id;
		this.practice_area = practice_area;
		this.is_deleted = is_deleted;
		this.organization_id = organization_id;
		this.organizationName = organizationName;
		this.setText(practice_area);
	}

	public PracticeArea(int _id, String practice_area, boolean is_deleted, int organization_id) {
		super();
		this._id = _id;
		this.practice_area = practice_area;
		this.is_deleted = is_deleted;
		this.organization_id = organization_id;
		this.setText(practice_area);
	}

	public int get_id() {
		return _id;
	}

	public void set_id(int _id) {
		this._id = _id;
	}

	public String getPracticeArea() {
		return practice_area;
	}

	public void setPractice_area(String practice_area) {
		this.practice_area = practice_area;
	}

	public boolean isIs_deleted() {
		return is_deleted;
	}

	public void setIs_deleted(boolean is_deleted) {
		this.is_deleted = is_deleted;
	}

	public int getOrganization_id() {
		return organization_id;
	}

	public void setOrganization_id(int organization_id) {
		this.organization_id = organization_id;
	}

	public String toString() {
		if (getOrganizationName().equals("")) {
			return practice_area;
		}
		return this.practice_area + " | " + getOrganizationName();
	}

	public String getOrganizationName() {
		return organizationName;
	}

	public void setOrganizationName(String organizationName) {
		this.organizationName = organizationName;
	}

}