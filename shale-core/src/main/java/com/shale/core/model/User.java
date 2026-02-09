package com.shale.core.model;

import java.util.Objects;

public final class User {
	private final int id;
	private final String nameFirst;
	private final String nameLast;
	private final String email;
	private final String color;
	private final boolean attorney;
	private final boolean admin;
	private final boolean deleted;
	private final Integer defaultOrganization; // nullable
	private final Integer organizationId; // nullable
	private final String initials;
	private final int shaleClientId; // for RLS/tenant logic

	private User(Builder b) {
		this.id = b.id;
		this.nameFirst = b.nameFirst;
		this.nameLast = b.nameLast;
		this.email = b.email;
		this.color = b.color;
		this.attorney = b.attorney;
		this.admin = b.admin;
		this.deleted = b.deleted;
		this.defaultOrganization = b.defaultOrganization;
		this.organizationId = b.organizationId;
		this.initials = b.initials;
		this.shaleClientId = b.shaleClientId;
	}

	public int getId() {
		return id;
	}

	public String getNameFirst() {
		return nameFirst;
	}

	public String getNameLast() {
		return nameLast;
	}

	public String getEmail() {
		return email;
	}

	public String getColor() {
		return color;
	}

	public boolean isAttorney() {
		return attorney;
	}

	public boolean isAdmin() {
		return admin;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public Integer getDefaultOrganization() {
		return defaultOrganization;
	}

	public Integer getOrganizationId() {
		return organizationId;
	}

	public String getInitials() {
		return initials;
	}

	public int getShaleClientId() {
		return shaleClientId;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private int id;
		private String nameFirst;
		private String nameLast;
		private String email;
		private String color;
		private boolean attorney;
		private boolean admin;
		private boolean deleted;
		private Integer defaultOrganization;
		private Integer organizationId;
		private String initials;
		private int shaleClientId;

		public Builder id(int id) {
			this.id = id;
			return this;
		}

		public Builder nameFirst(String v) {
			this.nameFirst = v;
			return this;
		}

		public Builder nameLast(String v) {
			this.nameLast = v;
			return this;
		}

		public Builder email(String v) {
			this.email = v;
			return this;
		}

		public Builder color(String v) {
			this.color = v;
			return this;
		}

		public Builder attorney(boolean v) {
			this.attorney = v;
			return this;
		}

		public Builder admin(boolean v) {
			this.admin = v;
			return this;
		}

		public Builder deleted(boolean v) {
			this.deleted = v;
			return this;
		}

		public Builder defaultOrganization(Integer v) {
			this.defaultOrganization = v;
			return this;
		}

		public Builder organizationId(Integer v) {
			this.organizationId = v;
			return this;
		}

		public Builder initials(String v) {
			this.initials = v;
			return this;
		}

		public Builder shaleClientId(int v) {
			this.shaleClientId = v;
			return this;
		}

		public User build() {
			return new User(this);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof User))
			return false;
		User u = (User) o;
		return id == u.id && shaleClientId == u.shaleClientId;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, shaleClientId);
	}

	@Override
	public String toString() {
		return "User{" +
				"id=" + id +
				", name='" + nameFirst + " " + nameLast + '\'' +
				", email='" + email + '\'' +
				", admin=" + admin +
				", shaleClientId=" + shaleClientId +
				'}';
	}
}
