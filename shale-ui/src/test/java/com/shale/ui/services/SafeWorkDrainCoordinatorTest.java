package com.shale.ui.services;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import com.shale.core.model.*;

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
	private static ApplicationUpdatePolicyCoordinator.Presentation policy(ApplicationUpdatePolicyState s){return new ApplicationUpdatePolicyCoordinator.Presentation(s,"","",1,"1.0.200",false,false);}
}
