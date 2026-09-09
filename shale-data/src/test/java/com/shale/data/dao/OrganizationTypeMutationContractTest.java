package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.shale.core.service.OrganizationServicePort.*;

final class OrganizationTypeMutationContractTest {
	@Test void definitionValuesEnforceImmutableBinaryIdentityAndStrictPresentationValues() throws Exception {
		Method validator=OrganizationTypeMutationDao.class.getDeclaredMethod("validateDefinition",String.class,String.class,String.class,String.class,int.class);
		validator.setAccessible(true);
		assertDoesNotThrow(()->invoke(validator,"medical_provider"," Provider ",null,"#A1B2C3",0));
		for(String key:List.of("","Medical_provider","medicalProvider","1provider","medical provider","medical-provider","medical.provider"))
			assertThrows(IllegalArgumentException.class,()->invoke(validator,key,"Provider",null,"#A1B2C3",0),key);
		assertThrows(IllegalArgumentException.class,()->invoke(validator,"provider","Provider",null,"#a1b2c3",0));
		assertThrows(IllegalArgumentException.class,()->invoke(validator,"provider","Provider",null,"#A1B2C",0));
		assertThrows(IllegalArgumentException.class,()->invoke(validator,"provider","Provider",null,"#A1B2C3",-1));
	}

	@Test void mutationSqlProtectsTenantConcurrencyOverlayPrimaryAndTransactions() throws Exception {
		String s=Files.readString(Path.of("src/main/java/com/shale/data/dao/OrganizationTypeMutationDao.java"));
		for(String required:List.of("SESSION_CONTEXT(N'ShaleClientId')","ShaleClientId=?","is_admin,0)=1","RowVer=?","WITH(UPDLOCK,HOLDLOCK)","OrganizationTypeId=?","IsPrimary=0","IsPrimary=1","Organizations SET OrganizationTypeId=?","con.rollback()","audit.append(con")) assertTrue(s.contains(required),required);
		assertTrue(s.contains("SystemKey=? COLLATE Latin1_General_100_BIN2"));
		assertTrue(s.contains("IsDeleted=1")&&s.contains("DeletedAt=SYSUTCDATETIME()")&&s.contains("DeletedByUserId=?"));
		assertTrue(s.contains("exact active assignment set"));
		assertFalse(s.matches("(?is).*\\bMERGE\\b.*"));
		assertFalse(s.matches("(?s).*\\(Integer\\)\\s*[^;]*getObject.*"),"JDBC numeric values use Number conversion");
	}

	@Test void rowVersionsAreDefensivelyCopiedByEveryMutationContract() {
		byte[] bytes={1,2};
		var update=new UpdateOrganizationTypeCommand(1,7,9,"Name",null,"#ABCDEF",0,bytes);
		var result=new OrganizationTypeMutationResult(1,"name",true,false,bytes);
		var order=new OrganizationTypeAssignmentOrder(2,bytes);
		bytes[0]=8; assertArrayEquals(new byte[]{1,2},update.expectedRowVer());assertArrayEquals(new byte[]{1,2},result.rowVer());assertArrayEquals(new byte[]{1,2},order.expectedRowVer());
		update.expectedRowVer()[0]=9;result.rowVer()[0]=9;assertArrayEquals(new byte[]{1,2},update.expectedRowVer());assertArrayEquals(new byte[]{1,2},result.rowVer());
	}

	@Test void auditVocabularyIsNarrowAndSupportsEveryPhaseOneCEvent() {
		for(var action:List.of(EntityActionAuditEvent.Action.CREATED,EntityActionAuditEvent.Action.OVERRIDE_CREATED,EntityActionAuditEvent.Action.UPDATED,EntityActionAuditEvent.Action.ACTIVATED,EntityActionAuditEvent.Action.DEACTIVATED,EntityActionAuditEvent.Action.REMOVED,EntityActionAuditEvent.Action.RESTORED))
			assertDoesNotThrow(()->EntityActionAuditEvent.now(7,9,EntityActionAuditEvent.EntityType.ORGANIZATION_TYPE,1,action,null,null,java.util.Map.of(EntityActionAuditEvent.MetadataKey.DEFINITION_ID,1)));
		for(var action:List.of(EntityActionAuditEvent.Action.ADDED,EntityActionAuditEvent.Action.REMOVED,EntityActionAuditEvent.Action.RESTORED,EntityActionAuditEvent.Action.PRIMARY_SET,EntityActionAuditEvent.Action.REORDERED))
			assertDoesNotThrow(()->EntityActionAuditEvent.now(7,9,EntityActionAuditEvent.EntityType.ORGANIZATION_ORGANIZATION_TYPE,1,action,null,null,java.util.Map.of(EntityActionAuditEvent.MetadataKey.ORGANIZATION_ID,2)));
		assertThrows(IllegalArgumentException.class,()->EntityActionAuditEvent.now(7,9,EntityActionAuditEvent.EntityType.ORGANIZATION_TYPE,1,EntityActionAuditEvent.Action.DELETED,null,null,java.util.Map.of()));
	}

	private static void invoke(Method method,Object... args){try{method.invoke(null,args);}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new RuntimeException(e.getCause());}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}
}
