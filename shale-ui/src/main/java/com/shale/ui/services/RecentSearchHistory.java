package com.shale.ui.services;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.shale.core.platform.AppPaths;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;

/** Query text only, in Shale's existing OS-user application-support location. */
public final class RecentSearchHistory {
    public static final int LIMIT = 10;
    private static final int MAX_QUERY_LENGTH = 2048;
    private static final int MAX_BYTES = 100_000;
    private final Path directory;

    public record Scope(int tenantId, int userId) {
        public Scope {
            if (tenantId <= 0 || userId <= 0) throw new IllegalArgumentException("Authenticated scope required");
        }
    }

    public RecentSearchHistory() { this(AppPaths.appSupportDir("Shale").resolve("recent-searches")); }
    public RecentSearchHistory(Path directory) { this.directory = directory; }

    public synchronized List<String> load(Scope scope) {
        Path file = file(scope);
        try {
            if (!Files.exists(file)) return List.of();
            try (var input = Files.newInputStream(file)) {
                byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) return List.of();
                var object = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!object.keySet().equals(java.util.Set.of("schemaVersion", "queries"))
                        || object.get("schemaVersion").getAsInt() != 1) return List.of();
                List<String> queries = new ArrayList<>();
                for (var entry : object.getAsJsonArray("queries")) {
                    if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) return List.of();
                    queries.add(entry.getAsString());
                }
                return normalize(queries);
            }
        } catch (IOException | RuntimeException unavailable) {
            return List.of();
        }
    }

    public synchronized void record(Scope scope, String query) throws IOException {
        if (!valid(query)) return;
        List<String> values = new ArrayList<>();
        values.add(query.trim());
        values.addAll(load(scope));
        save(scope, normalize(values));
    }

    public synchronized void remove(Scope scope, String query) throws IOException {
        save(scope, load(scope).stream().filter(v -> !v.equalsIgnoreCase(query.trim())).toList());
    }

    public synchronized void clear(Scope scope) throws IOException { Files.deleteIfExists(file(scope)); }

    static List<String> normalize(List<String> values) {
        var distinct = new HashSet<String>();
        return values.stream().filter(RecentSearchHistory::valid).map(String::trim)
                .filter(value -> distinct.add(value.toLowerCase(Locale.ROOT))).limit(LIMIT).toList();
    }

    private static boolean valid(String value) {
        return value != null && !value.trim().isEmpty() && value.trim().length() <= MAX_QUERY_LENGTH;
    }

    private Path file(Scope scope) {
        return directory.resolve("tenant-" + scope.tenantId() + "-user-" + scope.userId() + ".json");
    }

    private void save(Scope scope, List<String> values) throws IOException {
        Files.createDirectories(directory);
        var object = new JsonObject();
        object.addProperty("schemaVersion", 1);
        var queries = new JsonArray();
        values.forEach(queries::add);
        object.add("queries", queries);
        Path temporary = Files.createTempFile(directory, "history-", ".tmp");
        try {
            Files.writeString(temporary, object.toString(), StandardCharsets.UTF_8);
            Files.move(temporary, file(scope), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
