package com.shale.desktop;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class DesktopApiOriginTest {
	private static Properties properties(String key,String value){Properties p=new Properties();if(value!=null)p.setProperty(key,value);return p;}

	@Test void explicitSystemThenEnvironmentOverridePackagedConfiguration(){
		Properties packaged=properties(DesktopApiOrigin.PACKAGED_KEY,"https://packaged.example.test/");
		assertEquals(URI.create("https://system.example.test"),DesktopApiOrigin.resolve(properties(DesktopApiOrigin.OVERRIDE_KEY,"https://SYSTEM.example.test///"),Map.of(DesktopApiOrigin.OVERRIDE_KEY,"https://env.example.test"),packaged,"prod",true).orElseThrow());
		assertEquals(URI.create("https://env.example.test"),DesktopApiOrigin.resolve(new Properties(),Map.of(DesktopApiOrigin.OVERRIDE_KEY,"https://env.example.test/"),packaged,"prod",true).orElseThrow());
	}

	@Test void developmentDoesNotSilentlyUsePackagedProductionOrigin(){
		Properties packaged=properties(DesktopApiOrigin.PACKAGED_KEY,"https://production.example.test");
		assertTrue(DesktopApiOrigin.resolve(new Properties(),Map.of(),packaged,"dev",false).isEmpty());
		assertEquals(URI.create("http://localhost:8080"),DesktopApiOrigin.resolve(new Properties(),Map.of(DesktopApiOrigin.OVERRIDE_KEY," http://localhost:8080/ "),packaged,"dev",false).orElseThrow());
	}

	@Test void productionUsesOnlyValidHttpsPackagedOrigin(){
		Properties packaged=properties(DesktopApiOrigin.PACKAGED_KEY,"https://production.example.test/");
		assertEquals(URI.create("https://production.example.test"),DesktopApiOrigin.resolve(new Properties(),Map.of(),packaged,"prod",true).orElseThrow());
		packaged.setProperty(DesktopApiOrigin.PACKAGED_KEY,"http://production.example.test");
		assertThrows(IllegalStateException.class,()->DesktopApiOrigin.resolve(new Properties(),Map.of(),packaged,"prod",true));
	}

	@Test void packagedProductionResourceContainsValidatedRecordedAzureOrigin() throws Exception {
		Properties packaged=new Properties();
		try(var input=DesktopApiOriginTest.class.getClassLoader().getResourceAsStream("desktop-production.properties")){
			assertNotNull(input,"the production package must carry its API destination");
			packaged.load(input);
		}
		assertEquals(URI.create("https://shale-api-hsd6hrcya0g4amhv.southcentralus-01.azurewebsites.net"),
				DesktopApiOrigin.resolve(new Properties(),Map.of(),packaged,"prod",true).orElseThrow());
	}

	@Test void invalidExplicitOriginNeverFallsBack(){
		Properties packaged=properties(DesktopApiOrigin.PACKAGED_KEY,"https://production.example.test");
		for(String invalid:new String[]{"https://user:secret@example.test","https://example.test/api","https://example.test?q=x","https://example.test#x","http://example.test","not a uri"}){
			Properties system=properties(DesktopApiOrigin.OVERRIDE_KEY,invalid);
			assertThrows(IllegalStateException.class,()->DesktopApiOrigin.resolve(system,Map.of(),packaged,"prod",true),invalid);
		}
	}

	@Test void blankOverridesAreConsistentlyAbsent(){
		Properties packaged=properties(DesktopApiOrigin.PACKAGED_KEY,"https://production.example.test");
		assertEquals(URI.create("https://production.example.test"),DesktopApiOrigin.resolve(properties(DesktopApiOrigin.OVERRIDE_KEY,"  "),Map.of(DesktopApiOrigin.OVERRIDE_KEY,"\t"),packaged,"prod",true).orElseThrow());
	}
}
