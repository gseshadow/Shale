package com.shale.ui.services;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import com.shale.core.model.*;
import com.shale.core.update.*;

class SafeWorkDrainCoordinatorTest {
	@Test void workOpenedBeforeBlockIsGrandfatheredAndDrainEndsWithoutForceClose() {
		var states=new ArrayList<ApplicationVersionEnforcementState>();var gate=new SafeWorkDrainCoordinator(states::add);
		gate.apply(policy(ApplicationUpdatePolicyState.CURRENT));var intake=gate.tryStart("New Intake");assertNotNull(intake);
		gate.apply(policy(ApplicationUpdatePolicyState.REQUIRED_DEADLINE_REACHED));
		assertEquals(ApplicationVersionEnforcementState.DRAINING_REQUIRED_UPDATE,gate.state());
		assertNull(gate.tryStart("Case edit"),"new substantive work must not bypass the shared entry gate");
		intake.close();assertEquals(ApplicationVersionEnforcementState.BLOCKED_NEW_WORK,gate.state());
	}
	@Test void correctedPolicyReopensGateWithoutRestart() {
		var states=new ArrayList<ApplicationVersionEnforcementState>();var gate=new SafeWorkDrainCoordinator(states::add);
		gate.apply(policy(ApplicationUpdatePolicyState.REQUIRED_DEADLINE_REACHED));assertFalse(gate.permitsNewWork());
		gate.apply(policy(ApplicationUpdatePolicyState.CURRENT));assertTrue(gate.permitsNewWork());
		assertTrue(states.contains(ApplicationVersionEnforcementState.RECOVERING));
	}
	@Test void recommendationFutureDeadlineAndUnknownGraceNeverBlock() {
		var gate=new SafeWorkDrainCoordinator(s->{});
		for(var s:new ApplicationUpdatePolicyState[]{ApplicationUpdatePolicyState.RECOMMENDED,ApplicationUpdatePolicyState.REQUIRED_BEFORE_DEADLINE,ApplicationUpdatePolicyState.UNKNOWN}){
			gate.apply(policy(s));assertTrue(gate.permitsNewWork(),s+" must remain available");
		}
	}
	@Test void readinessIsReadyOnlyWithKnownEmptyAggregateEvidence() {
		var gate = new SafeWorkDrainCoordinator(s -> {});
		assertEquals(CooperativeShutdownReadiness.READY, gate.cooperativeShutdownReadiness());
		for (var type : MutationWorkflowType.values()) {
			try (var ignored = gate.tryStart(type)) {
				assertEquals(CooperativeShutdownReadiness.ACTIVE_MUTATION_WORKFLOW, gate.cooperativeShutdownReadiness(), type.name());
			}
		}
		try (var save = gate.saveStarted()) {
			assertEquals(CooperativeShutdownReadiness.SAVE_IN_FLIGHT, gate.cooperativeShutdownReadiness());
			save.close();
			assertEquals(CooperativeShutdownReadiness.READY, gate.cooperativeShutdownReadiness(), "save release must be idempotent");
		}
		gate.setPromptRequired(true);
		assertEquals(CooperativeShutdownReadiness.PROMPT_REQUIRED, gate.cooperativeShutdownReadiness());
		gate.setEvidenceKnown(false);
		assertEquals(CooperativeShutdownReadiness.UNKNOWN, gate.cooperativeShutdownReadiness());
		assertFalse(gate.cooperativeShutdownReadiness().permitsUnattendedShutdown());
	}
	@Test void multipleWorkflowLeasesAreExceptionSafeAndNeverDoubleRelease() {
		var gate = new SafeWorkDrainCoordinator(s -> {});
		var one = gate.tryStart(MutationWorkflowType.NEW_INTAKE);
		var two = gate.tryStart(MutationWorkflowType.CASE_EDIT);
		assertEquals(2, gate.activeWorkCount());
		one.close(); one.close();
		assertEquals(1, gate.activeWorkCount());
		two.close();
		assertEquals(CooperativeShutdownReadiness.READY, gate.cooperativeShutdownReadiness());
	}
	private static ApplicationUpdatePolicyCoordinator.Presentation policy(ApplicationUpdatePolicyState s){return new ApplicationUpdatePolicyCoordinator.Presentation(s,"","",1,"1.0.200",false,false);}
}
