package com.shale.core.service;

import java.time.LocalDate;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Shared organization application boundary for server/web read workflows.
 */
public interface OrganizationServicePort {

	List<OrganizationSummary> searchOrganizations(int shaleClientId, String query, int limit);

	Optional<OrganizationDetail> getOrganizationDetail(int organizationId, int shaleClientId);

	List<OrganizationTypeDefinition> listEffectiveOrganizationTypes(int shaleClientId);
	default List<OrganizationTypeDefinition> listOrganizationTypesForAdministration(int shaleClientId, int actorUserId) {
		throw new UnsupportedOperationException("Organization Type administration is not supported");
	}

	Optional<OrganizationTypeProfile> getOrganizationTypeProfile(int organizationId, int shaleClientId);

	/** Complete active and historical structured contact-method profile; structured rows are authoritative. */
	Optional<OrganizationStructuredContactProfile> findStructuredContactProfile(int shaleClientId, int organizationId);

	OrganizationTypeMutationResult createOrganizationType(CreateOrganizationTypeCommand command);
	OrganizationTypeMutationResult updateOrganizationType(UpdateOrganizationTypeCommand command);
	OrganizationTypeMutationResult setOrganizationTypeActive(OrganizationTypeLifecycleCommand command);
	OrganizationTypeMutationResult removeOrganizationType(OrganizationTypeLifecycleCommand command);
	OrganizationTypeMutationResult restoreOrganizationType(OrganizationTypeLifecycleCommand command);
	OrganizationTypeAssignmentMutationResult assignOrganizationType(AssignOrganizationTypeCommand command);
	OrganizationTypeAssignmentMutationResult removeOrganizationTypeAssignment(OrganizationTypeAssignmentLifecycleCommand command);
	OrganizationTypeAssignmentMutationResult restoreOrganizationTypeAssignment(OrganizationTypeAssignmentLifecycleCommand command);
	OrganizationTypeAssignmentMutationResult setPrimaryOrganizationType(SetPrimaryOrganizationTypeCommand command);
	OrganizationTypeAssignmentMutationResult replaceAndRemovePrimaryOrganizationType(ReplaceAndRemovePrimaryOrganizationTypeCommand command);
	List<OrganizationTypeAssignmentMutationResult> reorderOrganizationTypeAssignments(ReorderOrganizationTypeAssignmentsCommand command);

	int createOrganization(CreateOrganizationCommand command);

	boolean updateOrganization(UpdateOrganizationCommand command);

	/** Phase 2B authoritative create boundary. Fields and the exact type profile commit together. */
	default OrganizationAggregateResult createOrganizationAggregate(CreateOrganizationAggregateCommand command) {
		throw new UnsupportedOperationException("Aggregate Organization creation is not supported");
	}

	/** Phase 2B authoritative edit boundary. No caller may update the compatibility type independently. */
	default OrganizationAggregateResult updateOrganizationAggregate(UpdateOrganizationAggregateCommand command) {
		throw new UnsupportedOperationException("Aggregate Organization update is not supported");
	}

	/** Administrator-only lifecycle command; restores the existing aggregate identity in place. */
	default RestoreOrganizationResult restoreOrganization(RestoreOrganizationCommand command) {
		throw new UnsupportedOperationException("Organization restoration is not supported");
	}

	record RestoreOrganizationCommand(int shaleClientId, int actorUserId, int organizationId,
			byte[] expectedOrganizationRowVer) {
		public RestoreOrganizationCommand { expectedOrganizationRowVer = copyRowVer(expectedOrganizationRowVer); }
		@Override public byte[] expectedOrganizationRowVer() { return copyRowVer(expectedOrganizationRowVer); }
	}
	record RestoreOrganizationResult(int organizationId, int shaleClientId, String name, byte[] organizationRowVer) {
		public RestoreOrganizationResult { organizationRowVer = copyRowVer(organizationRowVer); }
		@Override public byte[] organizationRowVer() { return copyRowVer(organizationRowVer); }
	}

	record OrganizationSummary(
			int id,
			String name,
			Integer organizationTypeId,
			String organizationTypeName,
			String phone,
			String email,
			String website,
			String city,
			String state) {
	}

	record OrganizationDetail(
			int id,
			int shaleClientId,
			Integer organizationTypeId,
			String organizationTypeName,
			String name,
			String phone,
			String fax,
			String email,
			String website,
			String address1,
			String address2,
			String city,
			String state,
			String postalCode,
			String country,
			String notes,
			List<RelatedCaseSummary> relatedCases) {
	}

	enum OrganizationTypeOrigin { GLOBAL, TENANT }

	/** Selector-ready definition after resolving the tenant/global SystemKey overlay. */
	record OrganizationTypeDefinition(
			int organizationTypeId,
			Integer shaleClientId,
			String systemKey,
			String name,
			String description,
			String color,
			int sortOrder,
			boolean active,
			boolean deleted,
			OrganizationTypeOrigin origin,
			byte[] rowVer) {
		public OrganizationTypeDefinition { rowVer = copyRowVer(rowVer); }
		@Override public byte[] rowVer() { return copyRowVer(rowVer); }
	}

	/** An active assignment joined to its authoritative stored definition identity. */
	record AssignedOrganizationType(
			long assignmentId,
			int organizationTypeId,
			boolean primary,
			int sortOrder,
			OrganizationTypeDefinition definition,
			byte[] rowVer) {
		public AssignedOrganizationType { rowVer = copyRowVer(rowVer); }
		@Override public byte[] rowVer() { return copyRowVer(rowVer); }
		public boolean historical() { return !definition.active() || definition.deleted(); }
	}

	/** Read-only assignment aggregate; compatibilityConsistent never repairs either source. */
	record OrganizationTypeProfile(
			int organizationId,
			int shaleClientId,
			Integer compatibilityOrganizationTypeId,
			boolean compatibilityConsistent,
			List<AssignedOrganizationType> assignments) {
		public OrganizationTypeProfile { assignments = List.copyOf(assignments); }
	}

	enum OrganizationPhoneKind { MOBILE, HOME, WORK, FAX, OTHER, UNKNOWN }
	enum OrganizationEmailKind { PERSONAL, WORK, OTHER, UNKNOWN }
	enum OrganizationAddressKind { HOME, WORK, OTHER, UNKNOWN }
	enum OrganizationWebsiteKind { MAIN, WORK, OTHER, UNKNOWN }

	/** Compatibility state is intentionally per scalar concept and never repairs either representation. */
	enum CompatibilityState {
		BOTH_ABSENT, MATCHING, LEGACY_ONLY, STRUCTURED_ONLY, DIFFERENT, INVALID_PRIMARY;
		public boolean consistent() { return this == BOTH_ABSENT || this == MATCHING; }
	}

	record ContactLifecycle(Instant createdAt, Integer createdByUserId, Instant updatedAt,
			Integer updatedByUserId, Instant deletedAt, Integer deletedByUserId) { }

	record OrganizationPhoneNumber(long id, int shaleClientId, int organizationId,
			OrganizationPhoneKind kind, String rawKind, String displayNumber, String normalizedNumber,
			String extension, boolean primary, int sortOrder, boolean deleted, ContactLifecycle lifecycle,
			byte[] rowVer) {
		public OrganizationPhoneNumber { rowVer = copyRowVer(rowVer); }
		@Override public byte[] rowVer() { return copyRowVer(rowVer); }
		public boolean fax() { return kind == OrganizationPhoneKind.FAX; }
	}
	record OrganizationEmailAddress(long id, int shaleClientId, int organizationId,
			OrganizationEmailKind kind, String rawKind, String emailAddress, String normalizedEmail,
			boolean primary, int sortOrder, boolean deleted, ContactLifecycle lifecycle, byte[] rowVer) {
		public OrganizationEmailAddress { rowVer = copyRowVer(rowVer); }
		@Override public byte[] rowVer() { return copyRowVer(rowVer); }
	}
	record OrganizationAddress(long id, int shaleClientId, int organizationId,
			OrganizationAddressKind kind, String rawKind, String addressLine1, String addressLine2, String city,
			String stateOrProvince, String postalCode, String country, String legacyAddressText,
			boolean primary, int sortOrder, boolean deleted, ContactLifecycle lifecycle, byte[] rowVer) {
		public OrganizationAddress { rowVer = copyRowVer(rowVer); }
		@Override public byte[] rowVer() { return copyRowVer(rowVer); }
	}
	record OrganizationWebsite(long id, int shaleClientId, int organizationId,
			OrganizationWebsiteKind kind, String rawKind, String website, boolean primary, int sortOrder,
			boolean deleted, ContactLifecycle lifecycle, byte[] rowVer) {
		public OrganizationWebsite { rowVer = copyRowVer(rowVer); }
		@Override public byte[] rowVer() { return copyRowVer(rowVer); }
	}

	record StructuredContactCompatibility(CompatibilityState phone, CompatibilityState fax,
			CompatibilityState email, CompatibilityState address, CompatibilityState website) {
		public boolean compatibilityConsistent() {
			return phone.consistent() && fax.consistent() && email.consistent()
					&& address.consistent() && website.consistent();
		}
	}

	record OrganizationStructuredContactProfile(int organizationId, int shaleClientId,
			List<OrganizationPhoneNumber> phones, List<OrganizationEmailAddress> emails,
			List<OrganizationAddress> addresses, List<OrganizationWebsite> websites,
			Optional<OrganizationPhoneNumber> primaryPhone, Optional<OrganizationPhoneNumber> primaryFax,
			Optional<OrganizationEmailAddress> primaryEmail, Optional<OrganizationAddress> primaryAddress,
			Optional<OrganizationWebsite> primaryWebsite, StructuredContactCompatibility compatibility) {
		private static final Comparator<Object> CONTACT_ORDER = Comparator
				.comparing((Object value) -> deleted(value)).thenComparingInt(OrganizationStructuredContactProfile::order)
				.thenComparingLong(OrganizationStructuredContactProfile::id);
		public OrganizationStructuredContactProfile {
			phones = sorted(phones); emails = sorted(emails); addresses = sorted(addresses); websites = sorted(websites);
			primaryPhone = active(requiredOptional(primaryPhone)); primaryFax = active(requiredOptional(primaryFax));
			primaryEmail = active(requiredOptional(primaryEmail)); primaryAddress = active(requiredOptional(primaryAddress));
			primaryWebsite = active(requiredOptional(primaryWebsite)); java.util.Objects.requireNonNull(compatibility, "compatibility");
		}
		public List<OrganizationPhoneNumber> activePhones() { return phones.stream().filter(p -> !p.deleted()).toList(); }
		public List<OrganizationEmailAddress> activeEmails() { return emails.stream().filter(e -> !e.deleted()).toList(); }
		public List<OrganizationAddress> activeAddresses() { return addresses.stream().filter(a -> !a.deleted()).toList(); }
		public List<OrganizationWebsite> activeWebsites() { return websites.stream().filter(w -> !w.deleted()).toList(); }
		public boolean compatibilityConsistent() { return compatibility.compatibilityConsistent(); }
		private static <T> List<T> sorted(List<T> values) { var copy = new java.util.ArrayList<>(java.util.Objects.requireNonNull(values, "contact methods")); copy.sort((a,b)->CONTACT_ORDER.compare(a,b)); return List.copyOf(copy); }
		private static <T> Optional<T> requiredOptional(Optional<T> value) { return java.util.Objects.requireNonNull(value, "primary projection"); }
		private static <T> Optional<T> active(Optional<T> value) { return value.filter(v -> !deleted(v)); }
		private static boolean primary(Object v) { if(v instanceof OrganizationPhoneNumber x)return x.primary();if(v instanceof OrganizationEmailAddress x)return x.primary();if(v instanceof OrganizationAddress x)return x.primary();return ((OrganizationWebsite)v).primary(); }
		private static boolean deleted(Object v) { if(v instanceof OrganizationPhoneNumber x)return x.deleted();if(v instanceof OrganizationEmailAddress x)return x.deleted();if(v instanceof OrganizationAddress x)return x.deleted();return ((OrganizationWebsite)v).deleted(); }
		private static int order(Object v) { if(v instanceof OrganizationPhoneNumber x)return x.sortOrder();if(v instanceof OrganizationEmailAddress x)return x.sortOrder();if(v instanceof OrganizationAddress x)return x.sortOrder();return ((OrganizationWebsite)v).sortOrder(); }
		private static long id(Object v) { if(v instanceof OrganizationPhoneNumber x)return x.id();if(v instanceof OrganizationEmailAddress x)return x.id();if(v instanceof OrganizationAddress x)return x.id();return ((OrganizationWebsite)v).id(); }
	}

	/** Explicit contact participation; legacy callers can never accidentally submit an empty exact set. */
	sealed interface OrganizationContactMutation permits LegacyContactMutation, StructuredContactMutation { }
	record LegacyContactMutation() implements OrganizationContactMutation { }
	record StructuredContactMutation(OwnedContactCollection<StagedOrganizationPhone> phones,
			OwnedContactCollection<StagedOrganizationEmail> emails,
			OwnedContactCollection<StagedOrganizationAddress> addresses,
			OwnedContactCollection<StagedOrganizationWebsite> websites) implements OrganizationContactMutation {
		public StructuredContactMutation {
			phones=java.util.Objects.requireNonNull(phones); emails=java.util.Objects.requireNonNull(emails);
			addresses=java.util.Objects.requireNonNull(addresses); websites=java.util.Objects.requireNonNull(websites);
		}
	}
	record OwnedContactCollection<T>(boolean owned,List<T> rows) {
		public OwnedContactCollection { rows=List.copyOf(java.util.Objects.requireNonNull(rows)); }
		public static <T> OwnedContactCollection<T> omitted(){return new OwnedContactCollection<>(false,List.of());}
		public static <T> OwnedContactCollection<T> exact(List<T> rows){return new OwnedContactCollection<>(true,rows);}
	}
	/** Adapt a legacy create payload into an explicitly owned structured exact set. */
	static StructuredContactMutation structuredCreateFromLegacy(OrganizationFields f) {
		java.util.Objects.requireNonNull(f, "fields");
		var phones = new java.util.ArrayList<StagedOrganizationPhone>();
		if (meaningful(f.phone())) phones.add(new StagedOrganizationPhone(null,null,OrganizationPhoneKind.WORK,f.phone(),null,true,0,false));
		if (meaningful(f.fax())) phones.add(new StagedOrganizationPhone(null,null,OrganizationPhoneKind.FAX,f.fax(),null,phones.isEmpty(),phones.size(),false));
		var emails = meaningful(f.email()) ? List.of(new StagedOrganizationEmail(null,null,OrganizationEmailKind.WORK,f.email(),true,0,false)) : List.<StagedOrganizationEmail>of();
		var addresses = java.util.stream.Stream.of(f.address1(),f.address2(),f.city(),f.state(),f.postalCode(),f.country()).anyMatch(OrganizationServicePort::meaningful)
				? List.of(new StagedOrganizationAddress(null,null,OrganizationAddressKind.WORK,f.address1(),f.address2(),f.city(),f.state(),f.postalCode(),f.country(),null,true,0,false)) : List.<StagedOrganizationAddress>of();
		var websites = meaningful(f.website()) ? List.of(new StagedOrganizationWebsite(null,null,OrganizationWebsiteKind.MAIN,f.website(),true,0,false)) : List.<StagedOrganizationWebsite>of();
		return new StructuredContactMutation(OwnedContactCollection.exact(phones),OwnedContactCollection.exact(emails),OwnedContactCollection.exact(addresses),OwnedContactCollection.exact(websites));
	}
	private static boolean meaningful(String value) { return value != null && !value.trim().isEmpty(); }
	record StagedOrganizationPhone(Long id,byte[] expectedRowVer,OrganizationPhoneKind kind,String displayNumber,
			String extension,boolean primary,int sortOrder,boolean deleted) {
		public StagedOrganizationPhone { expectedRowVer=copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer(){return copyRowVer(expectedRowVer);}
	}
	record StagedOrganizationEmail(Long id,byte[] expectedRowVer,OrganizationEmailKind kind,String emailAddress,
			boolean primary,int sortOrder,boolean deleted) {
		public StagedOrganizationEmail { expectedRowVer=copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer(){return copyRowVer(expectedRowVer);}
	}
	record StagedOrganizationAddress(Long id,byte[] expectedRowVer,OrganizationAddressKind kind,String addressLine1,
			String addressLine2,String city,String stateOrProvince,String postalCode,String country,String legacyAddressText,
			boolean primary,int sortOrder,boolean deleted) {
		public StagedOrganizationAddress { expectedRowVer=copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer(){return copyRowVer(expectedRowVer);}
	}
	record StagedOrganizationWebsite(Long id,byte[] expectedRowVer,OrganizationWebsiteKind kind,String website,
			boolean primary,int sortOrder,boolean deleted) {
		public StagedOrganizationWebsite { expectedRowVer=copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer(){return copyRowVer(expectedRowVer);}
	}

	record StagedOrganizationTypeAssignment(Long assignmentId, int organizationTypeId, boolean primary,
			int sortOrder, byte[] expectedRowVer) {
		public StagedOrganizationTypeAssignment { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
	}

	record OrganizationFields(String name, String phone, String fax, String email, String website,
			String address1, String address2, String city, String state, String postalCode, String country,
			String notes) { }

	record CreateOrganizationAggregateCommand(int shaleClientId, int actorUserId, OrganizationFields fields,
			List<StagedOrganizationTypeAssignment> assignments, OrganizationContactMutation contactMutation) {
		public CreateOrganizationAggregateCommand { assignments = List.copyOf(assignments); contactMutation=java.util.Objects.requireNonNull(contactMutation); }
		public CreateOrganizationAggregateCommand(int tenant,int actor,OrganizationFields fields,List<StagedOrganizationTypeAssignment> assignments){this(tenant,actor,fields,assignments,new LegacyContactMutation());}
	}

	record UpdateOrganizationAggregateCommand(int organizationId, int shaleClientId, int actorUserId,
			byte[] expectedOrganizationRowVer, OrganizationFields fields,
			List<StagedOrganizationTypeAssignment> assignments, OrganizationContactMutation contactMutation) {
		public UpdateOrganizationAggregateCommand {
			expectedOrganizationRowVer = copyRowVer(expectedOrganizationRowVer);
			assignments = List.copyOf(assignments);
			contactMutation=java.util.Objects.requireNonNull(contactMutation);
		}
		public UpdateOrganizationAggregateCommand(int organizationId,int tenant,int actor,byte[] rowVer,OrganizationFields fields,List<StagedOrganizationTypeAssignment> assignments){this(organizationId,tenant,actor,rowVer,fields,assignments,new LegacyContactMutation());}
		@Override public byte[] expectedOrganizationRowVer() { return copyRowVer(expectedOrganizationRowVer); }
	}

	record OrganizationAggregateResult(int organizationId, byte[] organizationRowVer,
			OrganizationTypeProfile typeProfile, OrganizationFields fields,
			OrganizationStructuredContactProfile structuredContactProfile) {
		public OrganizationAggregateResult { organizationRowVer = copyRowVer(organizationRowVer); }
		public OrganizationAggregateResult(int id,byte[] rowVer,OrganizationTypeProfile profile){this(id,rowVer,profile,null,null);}
		@Override public byte[] organizationRowVer() { return copyRowVer(organizationRowVer); }
	}

	record CreateOrganizationTypeCommand(int shaleClientId, int actorUserId, String systemKey,
			Integer globalOrganizationTypeId, String name, String description, String color,
			int sortOrder, boolean active) { }
	record UpdateOrganizationTypeCommand(int organizationTypeId, int shaleClientId, int actorUserId,
			String name, String description, String color, int sortOrder, byte[] expectedRowVer) {
		public UpdateOrganizationTypeCommand { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
	}
	record OrganizationTypeLifecycleCommand(int organizationTypeId, int shaleClientId, int actorUserId,
			boolean active, byte[] expectedRowVer) {
		public OrganizationTypeLifecycleCommand { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
	}
	record OrganizationTypeMutationResult(int organizationTypeId, String systemKey, boolean active,
			boolean deleted, byte[] rowVer) {
		public OrganizationTypeMutationResult { rowVer = copyRowVer(rowVer); }
		@Override public byte[] rowVer() { return copyRowVer(rowVer); }
	}
	record AssignOrganizationTypeCommand(int shaleClientId, int actorUserId, int organizationId,
			int organizationTypeId) { }
	record OrganizationTypeAssignmentLifecycleCommand(int shaleClientId, int actorUserId,
			int organizationId, long assignmentId, byte[] expectedRowVer) {
		public OrganizationTypeAssignmentLifecycleCommand { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
	}
	record SetPrimaryOrganizationTypeCommand(int shaleClientId, int actorUserId, int organizationId,
			long assignmentId, byte[] expectedAssignmentRowVer) {
		public SetPrimaryOrganizationTypeCommand { expectedAssignmentRowVer = copyRowVer(expectedAssignmentRowVer); }
		@Override public byte[] expectedAssignmentRowVer() { return copyRowVer(expectedAssignmentRowVer); }
	}
	record ReplaceAndRemovePrimaryOrganizationTypeCommand(int shaleClientId, int actorUserId,
			int organizationId, long removedAssignmentId, byte[] removedExpectedRowVer,
			long replacementAssignmentId, byte[] replacementExpectedRowVer) {
		public ReplaceAndRemovePrimaryOrganizationTypeCommand {
			removedExpectedRowVer = copyRowVer(removedExpectedRowVer);
			replacementExpectedRowVer = copyRowVer(replacementExpectedRowVer);
		}
		@Override public byte[] removedExpectedRowVer() { return copyRowVer(removedExpectedRowVer); }
		@Override public byte[] replacementExpectedRowVer() { return copyRowVer(replacementExpectedRowVer); }
	}
	record OrganizationTypeAssignmentOrder(long assignmentId, byte[] expectedRowVer) {
		public OrganizationTypeAssignmentOrder { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
	}
	record ReorderOrganizationTypeAssignmentsCommand(int shaleClientId, int actorUserId,
			int organizationId, List<OrganizationTypeAssignmentOrder> orderedAssignments) {
		public ReorderOrganizationTypeAssignmentsCommand { orderedAssignments = List.copyOf(orderedAssignments); }
	}
	record OrganizationTypeAssignmentMutationResult(long assignmentId, int organizationId,
			int organizationTypeId, boolean primary, int sortOrder, boolean deleted, byte[] rowVer) {
		public OrganizationTypeAssignmentMutationResult { rowVer = copyRowVer(rowVer); }
		@Override public byte[] rowVer() { return copyRowVer(rowVer); }
	}

	record CreateOrganizationCommand(
			int shaleClientId,
			int actorUserId,
			String name,
			String phone,
			String fax,
			String email,
			String website,
			String address1,
			String address2,
			String city,
			String state,
			String postalCode,
			String country,
			String notes,
			Integer organizationTypeId) {
		public CreateOrganizationCommand(int tenant,int actor,String name,String phone,String fax,String email,String website,String address1,String address2,String city,String state,String postal,String country,String notes){this(tenant,actor,name,phone,fax,email,website,address1,address2,city,state,postal,country,notes,null);}
	}

	record UpdateOrganizationCommand(
			int organizationId,
			int shaleClientId,
			int actorUserId,
			String name,
			String phone,
			String fax,
			String email,
			String website,
			String address1,
			String address2,
			String city,
			String state,
			String postalCode,
			String country,
			String notes,
			Integer organizationTypeId,
			byte[] expectedOrganizationRowVer) {
		public UpdateOrganizationCommand { expectedOrganizationRowVer=copyRowVer(expectedOrganizationRowVer); }
		@Override public byte[] expectedOrganizationRowVer(){return copyRowVer(expectedOrganizationRowVer);}
		public UpdateOrganizationCommand(int id,int tenant,int actor,String name,String phone,String fax,String email,String website,String address1,String address2,String city,String state,String postal,String country,String notes){this(id,tenant,actor,name,phone,fax,email,website,address1,address2,city,state,postal,country,notes,null,null);}
	}

	record RelatedCaseSummary(
			long id,
			String name,
			LocalDate intakeDate,
			LocalDate statuteOfLimitationsDate,
			String responsibleAttorneyName,
			String partyRoleName,
			String side,
			boolean primary,
			String notes) {
	}

	private static byte[] copyRowVer(byte[] value) { return value == null ? null : value.clone(); }
}
