package com.shale.updater;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.shale.core.model.SemanticVersion;
class VersionComparatorCanonicalCompatibilityTest {
	@Test void strictModelAndPermissiveLegacyComparatorAgreeForCanonicalProductionVersions(){var values=List.of("1.0.99","1.0.127","1.0.130","1.1.0","2.0.0");for(String a:values)for(String b:values)assertEquals(Integer.signum(VersionComparator.compare(a,b)),Integer.signum(SemanticVersion.parse(a).compareTo(SemanticVersion.parse(b))),a+" vs "+b);}
}
