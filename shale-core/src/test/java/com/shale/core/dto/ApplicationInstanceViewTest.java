package com.shale.core.dto;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;import java.util.UUID;import org.junit.jupiter.api.Test;
import com.shale.core.model.*;
class ApplicationInstanceViewTest{
	@Test void desktopRequiresStableMachineAndPreservesNumericVersion(){var id=UUID.randomUUID();var v=new ApplicationInstanceView(1,id,ClientType.DESKTOP,new SemanticVersion(1,0,130),Instant.EPOCH,null);assertEquals(id,v.machineId());assertEquals("1.0.130",v.applicationVersion().toString());}
	@Test void rejectsFakeDesktopMachineAndInvalidLifecycle(){assertThrows(IllegalArgumentException.class,()->new ApplicationInstanceView(1,null,ClientType.DESKTOP,new SemanticVersion(1,0,0),Instant.EPOCH,null));assertThrows(IllegalArgumentException.class,()->new ApplicationInstanceView(1,UUID.randomUUID(),ClientType.WEB,new SemanticVersion(1,0,0),Instant.EPOCH,null));}
}
