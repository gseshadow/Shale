package com.shale.core.model;

import java.time.Instant;

public final class Organization {
	private final Integer id;
	private final Integer shaleClientId;
	private final Integer organizationTypeId;
	private final String organizationTypeName;
	private final String name;
	private final String phone;
	private final String fax;
	private final String email;
	private final String website;
	private final String address1;
	private final String address2;
	private final String city;
	private final String state;
	private final String postalCode;
	private final String country;
	private final String notes;
	private final boolean deleted;
	private final Instant createdAt;
	private final Instant updatedAt;

	private Organization(Builder b) {
		this.id = b.id;
		this.shaleClientId = b.shaleClientId;
		this.organizationTypeId = b.organizationTypeId;
		this.organizationTypeName = b.organizationTypeName;
		this.name = b.name;
		this.phone = b.phone;
		this.fax = b.fax;
		this.email = b.email;
		this.website = b.website;
		this.address1 = b.address1;
		this.address2 = b.address2;
		this.city = b.city;
		this.state = b.state;
		this.postalCode = b.postalCode;
		this.country = b.country;
		this.notes = b.notes;
		this.deleted = b.deleted;
		this.createdAt = b.createdAt;
		this.updatedAt = b.updatedAt;
	}

	public static Builder builder() {
		return new Builder();
	}

	public Integer getId() {
		return id;
	}

	public Integer getShaleClientId() {
		return shaleClientId;
	}

	public Integer getOrganizationTypeId() {
		return organizationTypeId;
	}

	public String getOrganizationTypeName() {
		return organizationTypeName;
	}

	public String getName() {
		return name;
	}

	public String getPhone() {
		return phone;
	}

	public String getFax() {
		return fax;
	}

	public String getEmail() {
		return email;
	}

	public String getWebsite() {
		return website;
	}

	public String getAddress1() {
		return address1;
	}

	public String getAddress2() {
		return address2;
	}

	public String getCity() {
		return city;
	}

	public String getState() {
		return state;
	}

	public String getPostalCode() {
		return postalCode;
	}

	public String getCountry() {
		return country;
	}

	public String getNotes() {
		return notes;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public static final class Builder {
		private Integer id;
		private Integer shaleClientId;
		private Integer organizationTypeId;
		private String organizationTypeName;
		private String name;
		private String phone;
		private String fax;
		private String email;
		private String website;
		private String address1;
		private String address2;
		private String city;
		private String state;
		private String postalCode;
		private String country;
		private String notes;
		private boolean deleted;
		private Instant createdAt;
		private Instant updatedAt;

		public Builder id(Integer v) {
			this.id = v;
			return this;
		}

		public Builder shaleClientId(Integer v) {
			this.shaleClientId = v;
			return this;
		}

		public Builder organizationTypeId(Integer v) {
			this.organizationTypeId = v;
			return this;
		}

		public Builder organizationTypeName(String v) {
			this.organizationTypeName = v;
			return this;
		}

		public Builder name(String v) {
			this.name = v;
			return this;
		}

		public Builder phone(String v) {
			this.phone = v;
			return this;
		}

		public Builder fax(String v) {
			this.fax = v;
			return this;
		}

		public Builder email(String v) {
			this.email = v;
			return this;
		}

		public Builder website(String v) {
			this.website = v;
			return this;
		}

		public Builder address1(String v) {
			this.address1 = v;
			return this;
		}

		public Builder address2(String v) {
			this.address2 = v;
			return this;
		}

		public Builder city(String v) {
			this.city = v;
			return this;
		}

		public Builder state(String v) {
			this.state = v;
			return this;
		}

		public Builder postalCode(String v) {
			this.postalCode = v;
			return this;
		}

		public Builder country(String v) {
			this.country = v;
			return this;
		}

		public Builder notes(String v) {
			this.notes = v;
			return this;
		}

		public Builder deleted(boolean v) {
			this.deleted = v;
			return this;
		}

		public Builder createdAt(Instant v) {
			this.createdAt = v;
			return this;
		}

		public Builder updatedAt(Instant v) {
			this.updatedAt = v;
			return this;
		}

		public Organization build() {
			return new Organization(this);
		}
	}
}
