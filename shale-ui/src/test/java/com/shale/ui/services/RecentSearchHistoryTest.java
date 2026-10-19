package com.shale.ui.services;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.ui.services.RecentSearchHistory.Scope;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecentSearchHistoryTest {
    @TempDir Path directory;
    private static final Scope FIRST = new Scope(7, 11);

    @Test void trimsDeduplicatesAndMovesCompletedQueriesToFrontPreservingReadableText() throws Exception {
        var history = new RecentSearchHistory(directory);
        history.record(FIRST, "  Ada Lovelace  ");
        history.record(FIRST, "Case 42");
        history.record(FIRST, " aDA Lovelace ");
        history.record(FIRST, " ");
        assertEquals(List.of("aDA Lovelace", "Case 42"), history.load(FIRST));
    }

    @Test void latestTenSurviveRestartAndStoreQueryTextOnly() throws Exception {
        var history = new RecentSearchHistory(directory);
        for (int i = 0; i < 15; i++) history.record(FIRST, "Query " + i);
        var reopened = new RecentSearchHistory(directory);
        assertEquals(10, reopened.load(FIRST).size());
        assertEquals("Query 14", reopened.load(FIRST).getFirst());
        assertEquals("Query 5", reopened.load(FIRST).getLast());
        String json = Files.readString(directory.resolve("tenant-7-user-11.json"));
        assertEquals(java.util.Set.of("schemaVersion", "queries"),
                com.google.gson.JsonParser.parseString(json).getAsJsonObject().keySet(),
                "No result details or identifying entity data may be persisted");
    }

    @Test void removalAndClearAffectOnlyTheAuthenticatedUserAndTenant() throws Exception {
        var history = new RecentSearchHistory(directory);
        var otherUser = new Scope(7, 12);
        var otherTenant = new Scope(8, 11);
        history.record(FIRST, "First");
        history.record(FIRST, "Second");
        history.record(otherUser, "Other user");
        history.record(otherTenant, "Other tenant");
        history.remove(FIRST, " FIRST ");
        assertEquals(List.of("Second"), history.load(FIRST));
        history.clear(FIRST);
        assertEquals(List.of(), history.load(FIRST));
        assertEquals(List.of("Other user"), history.load(otherUser));
        assertEquals(List.of("Other tenant"), history.load(otherTenant));
    }

    @Test void missingMalformedUnexpectedAndOversizedHistoryFailEmptyAndCanBeReplaced() throws Exception {
        var history = new RecentSearchHistory(directory);
        Path file = directory.resolve("tenant-7-user-11.json");
        assertTrue(history.load(FIRST).isEmpty());
        for (String invalid : List.of("{broken", "{\"schemaVersion\":2,\"queries\":[\"Old\"]}",
                "{\"schemaVersion\":1,\"queries\":[{\"name\":\"Private\"}]}",
                "{\"schemaVersion\":1,\"queries\":[\"Old\"],\"results\":[]}", "x".repeat(100001))) {
            Files.writeString(file, invalid);
            assertTrue(history.load(FIRST).isEmpty(), "Corrupt history must never render");
        }
        history.record(FIRST, "New");
        assertEquals(List.of("New"), history.load(FIRST));
        assertThrows(IllegalArgumentException.class, () -> new Scope(0, 11));
    }
}
