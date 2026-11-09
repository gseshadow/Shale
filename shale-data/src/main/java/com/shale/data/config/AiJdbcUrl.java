package com.shale.data.config;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Shared AI connection boundary. URLs select a destination, never TLS or authentication policy. */
public final class AiJdbcUrl {
    private static final Pattern SERVER = Pattern.compile(
            "jdbc:sqlserver://(?:[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*|\\[[0-9A-Fa-f:]+\\])(?:\\\\[A-Za-z0-9_-]+)?(?::[0-9]{1,5})?");
    private AiJdbcUrl() {}

    /** Conservative driver-compatible subset; fixed errors must never echo connection settings. */
    public static String validate(String url) {
        if (url == null || url.chars().anyMatch(Character::isISOControl)) throw invalid();
        String[] parts = url.split(";", -1);
        if (!SERVER.matcher(parts[0]).matches()) throw invalid();
        if (parts[0].contains("[")) {
            int open=parts[0].indexOf('['), close=parts[0].indexOf(']');
            try { java.net.URI.create("sqlserver://"+parts[0].substring(open,close+1)); }
            catch (IllegalArgumentException malformed) { throw invalid(); }
        }
        int colon = parts[0].lastIndexOf(':');
        if (colon > parts[0].lastIndexOf(']') && colon > "jdbc:sqlserver:".length()) {
            try {
                int port = Integer.parseInt(parts[0].substring(colon + 1));
                if (port < 1 || port > 65535) throw invalid();
            } catch (NumberFormatException malformed) { throw invalid(); }
        }
        Set<String> seen = new HashSet<>();
        for (int i = 1; i < parts.length; i++) {
            if (i == parts.length - 1 && parts[i].isEmpty()) continue;
            int equals = parts[i].indexOf('=');
            if (equals < 1) throw invalid();
            String key = parts[i].substring(0, equals).trim().toLowerCase(Locale.ROOT);
            String value = parts[i].substring(equals + 1);
            // An allowlist also excludes custom trust managers, pinned certificates, socket factories
            // and future driver authentication/TLS aliases that a protected-key denylist could miss.
            if (!Set.of("databasename", "applicationname").contains(key) || !seen.add(key)
                    || value.isBlank() || value.indexOf('{') >= 0 || value.indexOf('}') >= 0) throw invalid();
        }
        return url;
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("Invalid AI JDBC URL or protected connection setting.");
    }
}
