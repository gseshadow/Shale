package com.shale.ui.controller.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.shale.core.service.OrganizationServicePort.OrganizationTypeDefinition;
import com.shale.core.service.OrganizationServicePort.OrganizationTypeProfile;
import com.shale.core.service.OrganizationServicePort.StagedOrganizationTypeAssignment;

/**
 * Dialog-local Organization Type state. This class deliberately has no service/DAO dependency:
 * opening, searching, adding, removing, selecting a primary, and ordering cannot persist anything.
 */
public final class OrganizationTypeAssignmentStage {
	public record Item(Long assignmentId, OrganizationTypeDefinition definition, boolean primary,
			int sortOrder, byte[] expectedRowVer) {
		public Item { Objects.requireNonNull(definition); expectedRowVer = copy(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copy(expectedRowVer); }
		public boolean eligible() { return definition.active() && !definition.deleted(); }
		public boolean historical() { return !eligible(); }
	}

	private final List<OrganizationTypeDefinition> effectiveDefinitions;
	private final List<Item> baseline;
	private List<Item> staged;

	public static OrganizationTypeAssignmentStage forCreate(List<OrganizationTypeDefinition> effective) {
		return new OrganizationTypeAssignmentStage(effective, List.of());
	}

	public static OrganizationTypeAssignmentStage forEdit(List<OrganizationTypeDefinition> effective,
			OrganizationTypeProfile profile) {
		Objects.requireNonNull(profile, "profile");
		if (!profile.compatibilityConsistent()) {
			throw new IllegalStateException("Organization primary type compatibility is inconsistent; reconcile before editing.");
		}
		return new OrganizationTypeAssignmentStage(effective, profile.assignments().stream().map(
				a -> new Item(a.assignmentId(), a.definition(), a.primary(), a.sortOrder(), a.rowVer())).toList());
	}

	private OrganizationTypeAssignmentStage(List<OrganizationTypeDefinition> effective, List<Item> opening) {
		this.effectiveDefinitions = List.copyOf(effective);
		this.baseline = normalizedCopy(opening);
		this.staged = normalizedCopy(opening);
	}

	public List<Item> assigned() { return List.copyOf(staged); }
	public List<OrganizationTypeDefinition> available(String search) {
		String needle = search == null ? "" : search.strip().toLowerCase(java.util.Locale.ROOT);
		var assignedIds = staged.stream().map(i -> i.definition().organizationTypeId()).collect(java.util.stream.Collectors.toSet());
		return effectiveDefinitions.stream()
				.filter(d -> d.active() && !d.deleted() && !assignedIds.contains(d.organizationTypeId()))
				.filter(d -> needle.isEmpty() || d.name().toLowerCase(java.util.Locale.ROOT).contains(needle))
				.toList();
	}

	public void add(OrganizationTypeDefinition definition) {
		Objects.requireNonNull(definition, "definition");
		boolean effective = effectiveDefinitions.stream().anyMatch(d -> d.organizationTypeId() == definition.organizationTypeId());
		if (!effective || !definition.active() || definition.deleted()) throw new IllegalArgumentException("Only effective active Organization Types may be added.");
		if (staged.stream().anyMatch(i -> i.definition().organizationTypeId() == definition.organizationTypeId())) throw new IllegalArgumentException("Organization Type is already assigned.");
		var next = new ArrayList<>(staged);
		next.add(new Item(null, definition, next.isEmpty(), next.size(), null));
		staged = normalizedCopy(next);
	}

	public void setPrimary(int organizationTypeId) {
		Item selected = staged.stream().filter(i -> i.definition().organizationTypeId() == organizationTypeId).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Organization Type is not assigned."));
		if (!selected.eligible()) throw new IllegalArgumentException("An inactive or removed Organization Type cannot become primary.");
		staged = staged.stream().map(i -> new Item(i.assignmentId(), i.definition(),
				i.definition().organizationTypeId() == organizationTypeId, i.sortOrder(), i.expectedRowVer())).toList();
	}

	public void remove(int organizationTypeId) {
		Item selected = find(organizationTypeId);
		if (staged.size() == 1) throw new IllegalStateException("Every Organization requires a primary type.");
		if (selected.primary()) throw new IllegalStateException("Select a replacement primary before removing the current primary.");
		staged = normalizedCopy(staged.stream().filter(i -> i.definition().organizationTypeId() != organizationTypeId).toList());
	}

	public void replaceAndRemovePrimary(int removedTypeId, int replacementTypeId) {
		Item removed = find(removedTypeId);
		if (!removed.primary()) throw new IllegalArgumentException("The removed Organization Type is not primary.");
		setPrimary(replacementTypeId);
		remove(removedTypeId);
	}

	public void move(int organizationTypeId, int targetIndex) {
		if (targetIndex < 0 || targetIndex >= staged.size()) throw new IllegalArgumentException("Target order is outside the assignment list.");
		var next = new ArrayList<>(staged); Item item = find(organizationTypeId); next.remove(item); next.add(targetIndex, item); staged = normalizedCopy(next);
	}

	public boolean isDirty() { return !same(baseline, staged); }
	public boolean isValid() { return !staged.isEmpty() && staged.stream().filter(Item::primary).count() == 1 && staged.stream().filter(Item::primary).allMatch(Item::eligible); }
	public void discard() { staged = normalizedCopy(baseline); }
	public List<StagedOrganizationTypeAssignment> commandAssignments() {
		if (!isValid()) throw new IllegalStateException("Exactly one eligible primary Organization Type is required.");
		return staged.stream().map(i -> new StagedOrganizationTypeAssignment(i.assignmentId(),
				i.definition().organizationTypeId(), i.primary(), i.sortOrder(), i.expectedRowVer())).toList();
	}

	private Item find(int id) { return staged.stream().filter(i -> i.definition().organizationTypeId() == id).findFirst().orElseThrow(() -> new IllegalArgumentException("Organization Type is not assigned.")); }
	private static List<Item> normalizedCopy(List<Item> source) { var out = new ArrayList<Item>(); for (int i=0;i<source.size();i++){Item v=source.get(i);out.add(new Item(v.assignmentId(),v.definition(),v.primary(),i,v.expectedRowVer()));} return List.copyOf(out); }
	private static boolean same(List<Item> a,List<Item>b){if(a.size()!=b.size())return false;for(int i=0;i<a.size();i++){Item x=a.get(i),y=b.get(i);if(!Objects.equals(x.assignmentId(),y.assignmentId())||x.definition().organizationTypeId()!=y.definition().organizationTypeId()||x.primary()!=y.primary()||x.sortOrder()!=y.sortOrder())return false;}return true;}
	private static byte[] copy(byte[] value) { return value == null ? null : value.clone(); }
}
