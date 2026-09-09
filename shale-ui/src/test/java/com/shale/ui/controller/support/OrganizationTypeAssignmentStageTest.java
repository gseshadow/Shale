package com.shale.ui.controller.support;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.shale.core.service.OrganizationServicePort.*;

final class OrganizationTypeAssignmentStageTest {
	private static OrganizationTypeDefinition type(int id, boolean active, boolean deleted) {
		return new OrganizationTypeDefinition(id, 7, "type_"+id, "Type "+id, null, "#123456", id,
				active, deleted, OrganizationTypeOrigin.TENANT, new byte[]{(byte)id});
	}
	private static AssignedOrganizationType assigned(long id, OrganizationTypeDefinition d, boolean primary, int order) {
		return new AssignedOrganizationType(id,d.organizationTypeId(),primary,order,d,new byte[]{(byte)id});
	}

	@Test void createStagingIsLocalDirtyAndMaintainsExactlyOnePrimary() {
		var a=type(1,true,false);var b=type(2,true,false);var stage=OrganizationTypeAssignmentStage.forCreate(List.of(a,b));
		assertFalse(stage.isDirty());assertFalse(stage.isValid());
		stage.add(a);assertTrue(stage.assigned().get(0).primary(),"first assignment becomes primary");
		stage.add(b);stage.setPrimary(2);
		assertEquals(1,stage.assigned().stream().filter(OrganizationTypeAssignmentStage.Item::primary).count());
		assertFalse(stage.assigned().get(0).primary(),"selecting primary demotes the previous primary");
		assertTrue(stage.isDirty());assertEquals(List.of(),stage.available("type"));
		stage.remove(1);assertEquals(List.of(a),stage.available("1"),"removed staged additions return to choices");
		stage.discard();assertFalse(stage.isDirty(),"cancel/window close can discard without persistence");
	}

	@Test void primaryRemovalRequiresAtomicReplacementAndLastAssignmentCannotBeRemoved() {
		var a=type(1,true,false);var b=type(2,true,false);var stage=OrganizationTypeAssignmentStage.forCreate(List.of(a,b));stage.add(a);
		assertThrows(IllegalStateException.class,()->stage.remove(1));stage.add(b);
		assertThrows(IllegalStateException.class,()->stage.remove(1));stage.replaceAndRemovePrimary(1,2);
		assertEquals(2,stage.assigned().get(0).definition().organizationTypeId());assertTrue(stage.assigned().get(0).primary());
	}

	@Test void historicalIdentityRemainsVisibleButCannotBeAddedOrSelected() {
		var active=type(1,true,false);var historical=type(2,false,true);
		var profile=new OrganizationTypeProfile(9,7,1,true,List.of(assigned(11,active,true,0),assigned(12,historical,false,1)));
		var stage=OrganizationTypeAssignmentStage.forEdit(List.of(active),profile);
		assertTrue(stage.assigned().get(1).historical());assertThrows(IllegalArgumentException.class,()->stage.add(historical));
		assertThrows(IllegalArgumentException.class,()->stage.setPrimary(2));assertTrue(stage.available("").isEmpty());
	}

	@Test void inconsistentCompatibilityFailsClosedRatherThanManufacturingUiState() {
		var active=type(1,true,false);var profile=new OrganizationTypeProfile(9,7,2,false,List.of(assigned(11,active,true,0)));
		assertThrows(IllegalStateException.class,()->OrganizationTypeAssignmentStage.forEdit(List.of(active),profile));
	}
}
