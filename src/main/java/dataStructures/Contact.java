package dataStructures;

import java.io.Serializable;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Contact implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -31426319004934339L;

	private int _id;
	private Date date_of_birth;
	private String description = "";
	private String condition = "";
	private String notes = "";
	private String nameFirst = "";
	private String nameLast = "";
	private String nameWork = "";
	private String phoneCell = "";
	private String phoneWork = "";
	private String phoneHome = "";
	private String addressHome = "";
	private String addressWork = "";
	private String addressOther = "";
	private String emailPersonal = "";
	private String emailWork = "";
	private String emailOther = "";
	private boolean isClient = false;
	private boolean isExpert = false;
	private boolean isDeleted = false;
	private int organization_id;
	private int imageVersion;
	private String referredFrom = "";
	private boolean isDeceased = false;

	private boolean isForPlaintiff;
	private String relation = "";
	private LocalDate dob;
	private Organization organization;

//	private ArrayList<Matter> related;
	/*******************************************************************/
	public Contact() {
	}

	public Contact(int _id, Date date_of_birth, String description, String condition, String notes, String name_first, String name_last, String name_work, String phone_cell,
			String phone_home, String phone_work, String address_home, String address_work, String address_other, String email_personal, String email_work, String email_other,
			boolean is_client, boolean is_expert, boolean is_deleted, int organization_id, int imageVersion, String referredFrom, boolean isDeceased) {
		super();
		this._id = _id;
		this.date_of_birth = date_of_birth;
		this.description = description;
		this.condition = condition;
		this.notes = notes;
		this.nameFirst = name_first;
		this.nameLast = name_last;
		this.nameWork = name_work;
		this.phoneCell = phone_cell;
		this.phoneWork = phone_work;
		this.phoneHome = phone_home;
		this.addressHome = address_home;
		this.addressWork = address_work;
		this.addressOther = address_other;
		this.emailPersonal = email_personal;
		this.emailWork = email_work;
		this.emailOther = email_other;
		this.isClient = is_client;
		this.isExpert = is_expert;
		this.isDeleted = is_deleted;
		this.organization_id = organization_id;
		this.imageVersion = imageVersion;
		this.referredFrom = referredFrom;
		this.setDeceased(isDeceased);
	}
	/* constructors */

	private String generateNumber(String phoneNumber) {
		char[] chars = phoneNumber.toCharArray();
		ArrayList<String> tempPhone = new ArrayList<String>();
		String tempNumber = "";

		for (char c : chars) {

			if (-1 < Character.getNumericValue(c) && Character.getNumericValue(c) < 10) {
				tempPhone.add(String.valueOf(c));
			}
		}
		if (tempPhone.size() > 7) {
			tempPhone.add(0, "(");
			tempPhone.add(4, ")");
			tempPhone.add(8, "-");
		} else if (tempPhone.size() > 6) {
			tempPhone.add(3, "-");
		}
		for (String s : tempPhone) {
			tempNumber += s;
		}
		return tempNumber;
	}

	public void setPhoneNumberCell(String phoneNumberCell) {

		this.phoneCell = generateNumber(phoneNumberCell);
	}

	public void setPhoneNumberWork(String phoneNumberWork) {
		this.phoneWork = generateNumber(phoneNumberWork);
	}

	public void setPhoneNumberHome(String phoneNumberHome) {
		this.phoneHome = generateNumber(phoneNumberHome);
	}

	public String getRelation() {
		return relation;
	}

	public void setRelation(String relation) {
		this.relation = relation;
	}

	public String getNotes() {
		if (notes == null) {
			return "";
		}
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public boolean isForPlaintiff() {
		return isForPlaintiff;
	}

	public void setForPlaintiff(boolean isForPlaintiff) {
		this.isForPlaintiff = isForPlaintiff;
	}

	public String getExpertType() {
		return expertType;
	}

	public void setExpertType(String expertType) {
		this.expertType = expertType;
	}

	public List<Case> getRelatedMatters() {
		return relatedMatters;
	}

	public void setRelatedMatters(List<Case> relatedMatters) {
		this.relatedMatters = relatedMatters;
	}

	private String expertType;

	private List<Case> relatedMatters;

	/* Constructors */

	public Contact(String nameFirst, String nameLast) {
		this.nameFirst = nameFirst;
		this.nameLast = nameLast;
	}

	public String getNameFull() {
		return getName_first() + " " + getName_last();
	}

	public String getDob() {
		if (dob == null) {
			return "";
		}
		return dob.toString();
	}

	public LocalDate getDobLocalDateFormat() {
		if (date_of_birth != null)
			return date_of_birth.toLocalDate();
		return null;
	}

	public void setDob(LocalDate dob) {
		this.dob = dob;
	}

	public String getAge() {
		LocalDate now = LocalDate.now();
		if (date_of_birth != null) {
			return String.valueOf(now.getYear() - date_of_birth.toLocalDate().getYear());
		} else
			return "";
	}

	public String getCondition() {
		if (condition != null) {
			return condition;
		} else
			return "";
	}

	public void setCondition(String condition) {
		this.condition = condition;
	}

	/*******************************************************************/
	public int get_id() {
		return _id;
	}

	public void set_id(int _id) {
		this._id = _id;
	}

	public Date getDate_of_birth() {
		return date_of_birth;
	}

	public String getDate_of_birth_string() {
		if (date_of_birth != null)
			return date_of_birth.toString();
		else
			return "";
	}

	public void setDate_of_birth(Date date_of_birth) {
		this.date_of_birth = date_of_birth;
	}

	public String getDescription() {
		if (description == null)
			return "";
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getName_first() {
		if (nameFirst == null)
			return "";
		return nameFirst;
	}

	public void setName_first(String name_first) {
		this.nameFirst = name_first;
	}

	public String getName_last() {
		if (nameLast == null)
			return "";
		return nameLast;
	}

	public void setName_last(String name_last) {
		this.nameLast = name_last;
	}

	public String getName_work() {
		if (nameWork == null) {
			return "";
		}
		return nameWork;
	}

	public void setName_work(String name_work) {
		this.nameWork = name_work;
	}

	public String getPhone_cell() {
		if (phoneCell == null)
			return "";
		return phoneCell;
	}

	public void setPhone_cell(String phone_cell) {
		this.phoneCell = phone_cell;
	}

	public String getPhone_work() {
		if (phoneWork == null)
			return "";
		return phoneWork;
	}

	public void setPhone_work(String phone_work) {
		this.phoneWork = phone_work;
	}

	public String getPhone_home() {
		if (phoneHome == null)
			return "";
		return phoneHome;
	}

	public void setPhone_home(String phone_home) {
		this.phoneHome = phone_home;
	}

	public String getAddress_home() {
		if (addressHome == null)
			return "";
		return addressHome;
	}

	public void setAddress_home(String address_home) {
		this.addressHome = address_home;
	}

	public String getAddress_work() {
		if (addressWork == null)
			return "";
		return addressWork;
	}

	public void setAddress_work(String address_work) {
		this.addressWork = address_work;
	}

	public String getAddress_other() {
		if (addressOther == null)
			return "";
		return addressOther;
	}

	public void setAddress_other(String address_other) {
		this.addressOther = address_other;
	}

	public String getEmail_personal() {
		if (emailPersonal == null)
			return "";
		return emailPersonal;
	}

	public void setEmail_personal(String email_personal) {
		this.emailPersonal = email_personal;
	}

	public String getEmail_work() {
		if (emailWork == null)
			return "";
		return emailWork;
	}

	public void setEmail_work(String email_work) {
		this.emailWork = email_work;
	}

	public String getEmail_other() {
		if (emailOther == null)
			return "";
		return emailOther;
	}

	public void setEmail_other(String email_other) {
		this.emailOther = email_other;
	}

	public boolean getIs_client() {
		return isClient;
	}

	public void setIs_client(boolean is_client) {
		this.isClient = is_client;
	}

	public boolean getIs_expert() {
		return isExpert;
	}

	public void setIs_expert(boolean is_expert) {
		this.isExpert = is_expert;
	}

	public boolean isIs_deleted() {
		return isDeleted;
	}

	public void setIs_deleted(boolean is_deleted) {
		this.isDeleted = is_deleted;
	}

	public int getOrganization_id() {
		return organization_id;
	}

	public void setOrganization_id(int organization_id) {
		this.organization_id = organization_id;
	}

	public int getImageVersion() {
		return imageVersion;
	}

	public void setImageVersion(int imageVersion) {
		this.imageVersion = imageVersion;
	}

	public void setOrganization(Organization org) {
		this.organization_id = org.get_id();
		this.organization = org;
	}

	public Organization getOrganization() {
		return organization;
	}

	public String getReferredFrom() {
		return referredFrom;
	}

	public void setReferredFrom(String referredFrom) {
		this.referredFrom = referredFrom;
	}

	public boolean isDeceased() {
		return isDeceased;
	}

	public void setDeceased(boolean isDeceased) {
		this.isDeceased = isDeceased;
	}

	@Override
	public String toString() {
		return nameLast + ", " + nameFirst;

	}

}
