package com.shale.core.service;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
final class ContactProfileAggregateContractTest {
 @Test void assignmentConcurrencyTokensAreDefensivelyCopied(){byte[] rv={1,2};var a=new ContactServicePort.AssignedDefinition(9,new ContactServicePort.Definition(3,"expert","Expert",null,"#112233",0),false,rv);rv[0]=8;assertArrayEquals(new byte[]{1,2},a.rowVer());byte[] exposed=a.rowVer();exposed[1]=7;assertArrayEquals(new byte[]{1,2},a.rowVer());}
 @Test void aggregatePreservesExplicitDisplayNameAndCredentialsRemainSeparate(){var n=new ContactServicePort.StructuredName("Dr.","Ada",null,"Lovelace","Ada","III");var c=new ContactServicePort.UpdateContactProfileCommand(1,7,9,"Countess Lovelace",n,null,null,null,false,Instant.EPOCH,List.of(),List.of(),List.of(new ContactServicePort.IntendedAssignment(0,4,true,null)),List.of(),List.of(),List.of());assertEquals("Countess Lovelace",c.displayName());assertEquals("III",c.structuredName().suffix());assertFalse(c.displayName().contains("MD"));}
}
