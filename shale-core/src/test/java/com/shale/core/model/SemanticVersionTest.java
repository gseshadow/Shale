package com.shale.core.model;
import static org.junit.jupiter.api.Assertions.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
class SemanticVersionTest {
	@ParameterizedTest @ValueSource(strings={"0.0.0","1.0.127","1.0.130","2147483647.2147483647.2147483647"})
	void parsesCanonicalVersions(String text){assertEquals(text,SemanticVersion.parse(text).toString());}
	@Test void ordersComponentsNumerically(){assertTrue(SemanticVersion.parse("1.0.130").compareTo(SemanticVersion.parse("1.0.99"))>0);assertTrue(SemanticVersion.parse("1.1.0").compareTo(SemanticVersion.parse("1.0.999"))>0);assertTrue(SemanticVersion.parse("2.0.0").compareTo(SemanticVersion.parse("1.999.999"))>0);assertEquals(0,new SemanticVersion(1,2,3).compareTo(new SemanticVersion(1,2,3)));}
	static Stream<String> invalid(){return Stream.of(""," ","1","1.0","1.0.0.0","v1.0.0","1.0.-1","-1.0.0","1.0.0-beta","1.0.0+meta","nope","2147483648.0.0","01.0.0");}
	@ParameterizedTest @MethodSource("invalid") void rejectsNonCanonicalVersions(String text){assertThrows(IllegalArgumentException.class,()->SemanticVersion.parse(text));}
	@Test void rejectsNullAndNegativeConstruction(){assertThrows(NullPointerException.class,()->SemanticVersion.parse(null));assertThrows(IllegalArgumentException.class,()->new SemanticVersion(-1,0,0));}
}
