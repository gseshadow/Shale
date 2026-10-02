package com.shale.core.update;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WindowsInstallationRegistrationReaderTest {
	private static Map<String,String> record(String id,String sid,String root) { return Map.of("schemaVersion","1","installationId",id,"ownerSid",sid,"installRoot",root,"supportRoot",root); }
	@Test void validRegistrationResolvesOwnerPathsWithoutProcessEnvironment() {
		var reader=new WindowsInstallationRegistrationReader(()->List.of(record("11111111-1111-4111-8111-111111111111","S-1-5-21-1-2-3-1001","C:\\Users\\Alice\\AppData\\Local\\Shale")),ignored->WindowsInstallationRegistrationReader.State.PRESENT);
		var result=reader.read(); assertEquals(WindowsInstallationRegistrationReader.Classification.VALID,result.getFirst().classification());
		assertEquals("C:\\Users\\Alice\\AppData\\Local\\Shale\\update-attempts",OwnerUpdatePaths.from(result.getFirst().registration()).attemptStore().toString().replace('/','\\'));
	}
	@Test void staleAndReparseTargetsFailClosed() {
		for (var state:List.of(WindowsInstallationRegistrationReader.State.MISSING,WindowsInstallationRegistrationReader.State.REPARSE_UNSAFE)) {
			var result=new WindowsInstallationRegistrationReader(()->List.of(record("11111111-1111-4111-8111-111111111111","S-1-5-21-1-2-3-1001","C:\\Users\\Alice\\AppData\\Local\\Shale")),ignored->state).read().getFirst();
			assertEquals(state==WindowsInstallationRegistrationReader.State.MISSING ? WindowsInstallationRegistrationReader.Classification.STALE : WindowsInstallationRegistrationReader.Classification.REPARSE_UNSAFE,result.classification());
		}
	}
	@Test void duplicateIdentityMarksEveryClaimAndCollectionIsImmutable() {
		var id="11111111-1111-4111-8111-111111111111";
		var results=new WindowsInstallationRegistrationReader(()->List.of(record(id,"S-1-5-21-1-2-3-1001","C:\\Users\\Alice\\AppData\\Local\\Shale"),record(id,"S-1-5-21-1-2-3-1002","D:\\Users\\Bob\\AppData\\Local\\Shale")),ignored->WindowsInstallationRegistrationReader.State.PRESENT).read();
		assertTrue(results.stream().allMatch(r->r.classification()==WindowsInstallationRegistrationReader.Classification.DUPLICATE_INSTALLATION_ID));
		assertThrows(UnsupportedOperationException.class,()->results.clear());
	}
	@Test void invalidSchemaOwnerAndOwnerPathAreClassifiedWithoutRawException() {
		var valid=record("11111111-1111-4111-8111-111111111111","S-1-5-21-1-2-3-1001","C:\\Users\\Alice\\AppData\\Local\\Shale");
		var schema=new java.util.HashMap<>(valid); schema.put("schemaVersion","2");
		var owner=new java.util.HashMap<>(valid); owner.put("ownerSid","alice");
		var path=new java.util.HashMap<>(valid); path.put("supportRoot","D:\\Users\\Alice\\AppData\\Local\\Shale");
		var results=new WindowsInstallationRegistrationReader(()->List.of(schema,owner,path),ignored->WindowsInstallationRegistrationReader.State.PRESENT).read();
		assertEquals(List.of(WindowsInstallationRegistrationReader.Classification.INVALID_SCHEMA,WindowsInstallationRegistrationReader.Classification.INVALID_OWNER,WindowsInstallationRegistrationReader.Classification.INVALID_PATH),results.stream().map(WindowsInstallationRegistrationReader.Result::classification).toList());
	}
	@Test void sourceFailureIsUnavailable() {
		var results=new WindowsInstallationRegistrationReader(()->{ throw new java.io.IOException("secret detail"); },ignored->WindowsInstallationRegistrationReader.State.PRESENT).read();
		assertEquals(List.of(new WindowsInstallationRegistrationReader.Result(WindowsInstallationRegistrationReader.Classification.UNAVAILABLE,null)),results);
	}
}
