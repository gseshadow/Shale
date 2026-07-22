package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

final class CaseDaoPartySideSystemKeySafetyTest {
    @Test
    void builtInPartySideKeysAreVerifiedFromMigrationSeedData() throws Exception {
        String phase1 = Files.readString(Path.of("../docs/sql/2026-04-06_partysides_system_key_phase1.sql"));
        String phase2 = Files.readString(Path.of("../docs/sql/2026-04-06_partysides_global_activation_phase2.sql"));

        for (String seed : List.of("('represented', 'Represented')", "('opposing', 'Opposing')", "('neutral', 'Neutral')")) {
            assertTrue(phase1.contains(seed), "Phase 1 migration verifies PartySides built-in seed " + seed);
            assertTrue(phase2.contains(seed), "Phase 2 migration verifies PartySides global built-in seed " + seed);
        }
    }

    @Test
    void partySideSystemKeyResolutionIsNullSafeNormalizedAndNameIndependent() throws Exception {
        Method resolver = CaseDao.class.getDeclaredMethod("resolvePartySideSystemKey", String.class, String.class);
        resolver.setAccessible(true);

        assertEquals("represented", resolver.invoke(null, "represented", "Renamed Client Side"));
        assertEquals("opposing", resolver.invoke(null, " OPPOSING ", "Renamed Adverse Side"));
        assertEquals("neutral", resolver.invoke(null, "Neutral", "Renamed Neutral Side"));
        assertEquals("custom", resolver.invoke(null, " Custom ", "Represented"),
                "Unknown custom SystemKeys are preserved for generic custom behavior instead of being renamed by display Name");
        assertNull(resolver.invoke(null, null, "Represented"),
                "A custom side named like a built-in without the protected SystemKey must remain generic");
        assertNull(resolver.invoke(null, "  ", "opposing"),
                "Blank SystemKey must not be backfilled from display Name");
        assertNull(resolver.invoke(null, null, null), "Null SystemKey must be safe and generic");
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void effectivePartySideOverlayUsesSystemKeyOnlyAndPreservesDisplayNames() throws Exception {
        Class<?> rowClass = Class.forName("com.shale.data.dao.CaseDao$PartySideLookupRow");
        Constructor<?> ctor = rowClass.getDeclaredConstructor(Long.class, String.class, String.class);
        ctor.setAccessible(true);
        Object globalRepresented = ctor.newInstance(1L, "Represented", "represented");
        Object renamedTenantRepresented = ctor.newInstance(2L, "Our Clients", " represented ");
        Object customNamedRepresented = ctor.newInstance(3L, "Represented", "tenant_represented_label");
        Object unkeyedNamedOpposing = ctor.newInstance(4L, "Opposing", null);
        Object blankNamedNeutral = ctor.newInstance(5L, "Neutral", " ");
        Object unknownCustom = ctor.newInstance(6L, "Interested Party", "interested");

        Method resolver = CaseDao.class.getDeclaredMethod("resolveEffectivePartySides", List.class, List.class);
        resolver.setAccessible(true);
        List rows = (List) resolver.invoke(null,
                List.of(globalRepresented),
                List.of(renamedTenantRepresented, customNamedRepresented, unkeyedNamedOpposing, blankNamedNeutral, unknownCustom));

        Method name = rowClass.getDeclaredMethod("name");
        Method systemKey = rowClass.getDeclaredMethod("systemKey");
        name.setAccessible(true);
        systemKey.setAccessible(true);

        assertTrue(rows.stream().anyMatch(row -> "Our Clients".equals(invoke(name, row))
                && " represented ".equals(invoke(systemKey, row))),
                "A tenant override keeps the built-in behavior through SystemKey even when its display Name changes");
        assertTrue(rows.stream().anyMatch(row -> "Represented".equals(invoke(name, row))
                && "tenant_represented_label".equals(invoke(systemKey, row))),
                "A custom side using a built-in display label without the protected SystemKey remains a separate generic value");
        assertTrue(rows.stream().anyMatch(row -> "Opposing".equals(invoke(name, row))
                && invoke(systemKey, row) == null),
                "Null SystemKey rows remain unkeyed generic values with display labels preserved");
        assertTrue(rows.stream().anyMatch(row -> "Neutral".equals(invoke(name, row))
                && " ".equals(invoke(systemKey, row))),
                "Blank SystemKey rows remain unkeyed generic values with display labels preserved");
        assertTrue(rows.stream().anyMatch(row -> "Interested Party".equals(invoke(name, row))
                && "interested".equals(invoke(systemKey, row))),
                "Unknown custom SystemKeys remain tenant-created generic values");
    }

    @Test
    void sourceContainsNoPartySideNameFallbackOrNumericIdBehavior() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String controller = Files.readString(Path.of("../shale-ui/src/main/java/com/shale/ui/controller/CaseController.java"));
        String intake = Files.readString(Path.of("../shale-ui/src/main/java/com/shale/ui/controller/NewIntakeController.java"));

        assertFalse(source.contains("resolveLegacyPartySideSystemKeyFromName"));
        assertFalse(source.contains("rs.getString(\"Name\"))\n\t\t);\n\t}\n\n\tprivate static List<PartySideLookupRow>"),
                "PartySide lookup mapping should not infer SystemKey from Name");
        assertFalse(source.matches("(?s).*PartySideId\\s*[=<>].*"),
                "PartySides behavior must not depend on a numeric PartySideId");
        assertTrue(controller.contains("side.name()"),
                "CaseController selector labels intentionally render PartySides.Name");
        assertTrue(intake.contains("side.name()"),
                "NewIntakeController selector labels intentionally render PartySides.Name");
    }

    private static Object invoke(Method method, Object target) {
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
