package dataStructures;

import java.io.Serializable;

public class Status implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6358956767363387779L;

	private int _id;
	private String status;
	private boolean for_case;
	private boolean deleted;
	private int organization_id;

	public Status() {

	}

	public Status(int _id, String status, boolean for_case, boolean deleted, int organization_id) {
		super();
		this._id = _id;
		this.status = status;
		this.for_case = for_case;
		this.deleted = deleted;
		this.organization_id = organization_id;
	}

	public int get_id() {
		return _id;
	}

	public void set_id(int _id) {
		this._id = _id;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public boolean isFor_case() {
		return for_case;
	}

	public void setFor_case(boolean for_case) {
		this.for_case = for_case;
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

	public String getText() {
		return status;
	}

	public String toString() {
		return status;
	}
}
