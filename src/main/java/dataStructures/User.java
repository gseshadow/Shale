package dataStructures;

import java.io.Serializable;

public class User implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3607491173437534906L;
	private String nameFirst;
	private String nameLast;
	private String email;
	private String password;
	private String color;

	private boolean is_attorney = false;
	private boolean is_admin = false;
	private boolean is_deleted = false;
	private int default_organization;
	private String initials;

	private boolean showAsList = false;
	private boolean showPotential = true;
	private boolean showAccepted = true;
	private boolean showDenied = false;
	private boolean showClosed = false;
	private boolean showTransferred = false;
	private boolean stayLoggedIn = false;
	private boolean showActiveView = false;
	private double iconSize = 5;

	private boolean loggedIn = false;

	private int _id;

	public User() {

	}

	public User(int id, String nameFirst, String nameLast, String email, String password, String color, boolean is_attorney, boolean is_admin,
			boolean is_deleted, int default_organization) {
		this._id = id;
		this.nameFirst = nameFirst;
		this.nameLast = nameLast;
		this.email = email;
		this.password = password;
		this.color = color;
		this.is_attorney = is_attorney;
		this.is_admin = is_admin;
		this.is_deleted = is_deleted;
		this.default_organization = default_organization;
	}

	public String getNameFirst() {
		if (nameFirst == null)
			return "";
		return nameFirst;
	}

	public void setNameFirst(String nameFirst) {
		this.nameFirst = nameFirst;
	}

	public String getNameLast() {
		if (nameLast == null)
			return "";
		return nameLast;
	}

	public void setNameLast(String nameLast) {
		this.nameLast = nameLast;
	}

	public String getNameFull() {
		String s = getNameFirst() + " " + getNameLast();
		return s;
	}

	public String getEmail() {
		if (email == null)
			return "";
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPassword() {
		if (password == null)
			return "";
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getColor() {
		if (color == null)
			return "";
		return color;
	}

	public void setColor(String color) {
		this.color = color;
	}

	public boolean isIs_attorney() {
		return is_attorney;
	}

	public void setIs_attorney(boolean is_attorney) {
		this.is_attorney = is_attorney;
	}

	public boolean isIs_admin() {
		return is_admin;
	}

	public void setIs_admin(boolean is_admin) {
		this.is_admin = is_admin;
	}

	public boolean isIs_deleted() {
		return is_deleted;
	}

	public void setIs_deleted(boolean is_deleted) {
		this.is_deleted = is_deleted;
	}

	public int getDefault_organization() {
		return default_organization;
	}

	public void setDefault_organization(int default_organization) {
		this.default_organization = default_organization;
	}

	public boolean isShowAsList() {
		return showAsList;
	}

	public void setShowAsList(boolean showAsList) {
		this.showAsList = showAsList;
	}

	public boolean isShowPotential() {
		return showPotential;
	}

	public void setShowPotential(boolean showPotential) {
		this.showPotential = showPotential;
	}

	public boolean isShowAccepted() {
		return showAccepted;
	}

	public void setShowAccepted(boolean showAccepted) {
		this.showAccepted = showAccepted;
	}

	public boolean isShowDenied() {
		return showDenied;
	}

	public void setShowDenied(boolean showDenied) {
		this.showDenied = showDenied;
	}

	public boolean isShowClosed() {
		return showClosed;
	}

	public void setShowClosed(boolean showClosed) {
		this.showClosed = showClosed;
	}

	public boolean isStayLoggedIn() {
		return stayLoggedIn;
	}

	public void setStayLoggedIn(boolean stayLoggedIn) {
		this.stayLoggedIn = stayLoggedIn;
	}

	public void setLoggedIn(boolean isLoggedIn) {
		this.loggedIn = isLoggedIn;
	}

	public boolean isLoggedIn() {
		return loggedIn;
	}

	public int get_id() {
		return _id;
	}

	public void set_id(int _id) {
		this._id = _id;
	}

	public String getInitials() {
		if (initials == null)
			return "";
		return initials.toUpperCase();
	}

	public void setInitials(String initials) {
		if (initials != null)
			this.initials = initials.toUpperCase();
	}

	@Override
	public String toString() {
		return getNameFull();
	}

	public double getIconSize() {
		return iconSize;
	}

	public void setIconSize(double iconSize) {
		this.iconSize = iconSize;
	}

	public boolean isShowTransferred() {
		return showTransferred;
	}

	public void setShowTransferred(boolean showTransferred) {
		this.showTransferred = showTransferred;
	}

	public boolean isShowActiveView() {
		return showActiveView;
	}

	public void setShowActiveView(boolean showActiveView) {
		this.showActiveView = showActiveView;
	}
}
