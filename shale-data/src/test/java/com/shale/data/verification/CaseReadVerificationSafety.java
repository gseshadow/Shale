package com.shale.data.verification;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Test-tool boundary only. No application configuration or connection URL is consumed. */
final class CaseReadVerificationSafety {
    static final String OPT_IN = "LOCAL_DISPOSABLE_CASE_READS";
    static final Pattern OWNED_NAME = Pattern.compile("ShaleCaseReadVerify_[a-f0-9]{32}");
    private static final Pattern PRODUCTION = Pattern.compile(
            "(?i)^(SHALE_(APP|RT|AUTH|SERVER|SUGGESTION).*|SPRING_DATASOURCE.*|SQLCONNSTR_.*|SQLAZURECONNSTR_.*)$");

    static int configuration(Map<String, String> env, Map<String, String> properties) {
        require(OPT_IN.equals(env.get("SHALE_CASE_VERIFY_OPT_IN")));
        for (var e : env.entrySet()) require(!PRODUCTION.matcher(e.getKey()).matches() || e.getValue().isBlank());
        for (var e : properties.entrySet()) require(!PRODUCTION.matcher(e.getKey()).matches() || e.getValue().isBlank());
        for (String key : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "MAVEN_ARGS"))
            require(env.getOrDefault(key, "").isBlank());
        int port = Integer.parseInt(env.get("SHALE_CASE_VERIFY_PORT"));
        require(port >= 1024 && port <= 65535);
        require(!env.getOrDefault("SHALE_CASE_VERIFY_ADMIN_USER", "").isBlank());
        require(!env.getOrDefault("SHALE_CASE_VERIFY_ADMIN_PASSWORD", "").isBlank());
        require(!env.getOrDefault("SHALE_CASE_VERIFY_SCHEMA_SOURCE", "").isBlank());
        return port;
    }

    static String url(int port, String database) {
        require(port >= 1024 && port <= 65535);
        require(database.equals("master") || OWNED_NAME.matcher(database).matches());
        return "jdbc:sqlserver://127.0.0.1:" + port + ";databaseName=" + database
                + ";encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=15000;applicationName=CaseReadLocalVerifier";
    }

    static List<String> schema(Path path, String expectedHash) throws Exception {
        require(Files.isRegularFile(path) && Files.size(path) <= 8 * 1024 * 1024);
        byte[] bytes = Files.readAllBytes(path);
        require(expectedHash != null && expectedHash.matches("[a-fA-F0-9]{64}"));
        require(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equalsIgnoreCase(expectedHash));
        return ddl(new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", ""));
    }

    /** Deliberately restricted schema-only SSMS-style batches, not a general SQL/migration runner. */
    static List<String> ddl(String source) {
        // Strip comments/literals lexically, so quoted/commented GO is never a batch separator.
        String masked = mask(source);
        var separators = Pattern.compile("(?im)^\\s*GO\\s*$").matcher(masked);
        List<String> result = new ArrayList<>();
        int start = 0;
        while (separators.find()) {
            addDdl(source.substring(start, separators.start()), masked.substring(start, separators.start()), result);
            start = separators.end();
        }
        addDdl(source.substring(start), masked.substring(start), result);
        require(!result.isEmpty());
        return List.copyOf(result);
    }

    private static void addDdl(String original, String mask, List<String> result) {
        String sql = mask.trim().toUpperCase(java.util.Locale.ROOT);
        // SSMS may emit multiple spaces after CREATE. Keep the executable definition untouched.
        sql = sql.replaceFirst("^CREATE\\s+FUNCTION\\s+", "CREATE FUNCTION ");
        if (sql.isEmpty()) return;
        boolean policy = sql.startsWith("CREATE SECURITY POLICY ");
        boolean function = sql.startsWith("CREATE FUNCTION ");
        boolean reviewedScalar = function && reviewedScalar(original);
        require(sql.matches("(?s)(CREATE (SCHEMA|TABLE|FUNCTION|SECURITY POLICY|(?:UNIQUE )?(?:NONCLUSTERED |CLUSTERED )?INDEX) .*|ALTER TABLE .*|SET (ANSI_NULLS|QUOTED_IDENTIFIER|ANSI_PADDING|ANSI_WARNINGS|ARITHABORT|CONCAT_NULL_YIELDS_NULL) ON\\s*;?|SET NUMERIC_ROUNDABORT OFF\\s*;?)"));
        String forbidden = "EXEC(?:UTE)?|USE|DROP|MERGE|GRANT|DENY|REVOKE|LOGIN|USER|DATABASE|BACKUP|RESTORE|OPENROWSET|OPENDATASOURCE|OPENQUERY|EXTERNAL|TRIGGER|PROCEDURE|NOCHECK|DISABLE|TRUNCATE";
        require(!Pattern.compile("\\b(?:" + forbidden + ")\\b").matcher(sql).find());
        String dataStatementCheck = sql;
        if (sql.startsWith("ALTER TABLE ") && sql.contains("FOREIGN KEY") && sql.contains("REFERENCES"))
            dataStatementCheck = sql.replaceAll("\\bON\\s+(?:DELETE|UPDATE)\\s+(?:CASCADE|NO\\s+ACTION|SET\\s+NULL|SET\\s+DEFAULT)\\b", "");
        if (!policy) require(!Pattern.compile("\\b(?:INSERT|UPDATE|DELETE)\\b").matcher(dataStatementCheck).find());
        if (!function) require(!Pattern.compile("\\bSELECT\\b").matcher(sql).find());
        require(!Pattern.compile("[\\w\\]]\\s*\\.\\s*[\\w\\[\\]]+\\s*\\.").matcher(sql).find());
        require(!Pattern.compile("(?m)^\\s*:").matcher(sql).find()); // no sqlcmd directives
        require(reviewedScalar || sql.indexOf(';') < 0 || sql.indexOf(';') == sql.length() - 1);
        var mutations = Pattern.compile("\\b(?:CREATE|ALTER)\\b").matcher(sql);
        int count = 0;
        while (mutations.find()) count++;
        require(count <= 1);
        if (policy) require(Pattern.compile("STATE\\s*=\\s*ON").matcher(sql).find()
                && !Pattern.compile("\\bOFF\\b").matcher(sql).find());
        if (function) require(reviewedScalar || sql.contains("RETURNS TABLE") && sql.contains("SCHEMABINDING"));
        result.add(original);
    }

    /** Only the reviewed recovered ToBitNV definition, never arbitrary scalar bodies. */
    private static boolean reviewedScalar(String original) {
        try {
            String canonical = original.strip().replace("\r\n", "\n");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)))
                    .equals("4a66a56b195010136d90909b62a241066f3f10a8089798e4ce99b8673732d10b");
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static String mask(String sql) {
        StringBuilder out = new StringBuilder(sql);
        int i = 0;
        while (i < sql.length()) {
            int start = i;
            if (sql.startsWith("--", i)) {
                while (i < sql.length() && sql.charAt(i) != '\n') i++;
            } else if (sql.startsWith("/*", i)) {
                int depth = 1;
                i += 2;
                while (i < sql.length() && depth > 0) {
                    if (sql.startsWith("/*", i)) { depth++; i += 2; }
                    else if (sql.startsWith("*/", i)) { depth--; i += 2; }
                    else i++;
                }
                require(depth == 0);
            } else if (sql.charAt(i) == '\'') {
                i++;
                boolean ended = false;
                while (i < sql.length()) {
                    if (sql.charAt(i++) == '\'') {
                        if (i < sql.length() && sql.charAt(i) == '\'') i++;
                        else { ended = true; break; }
                    }
                }
                require(ended);
            } else { i++; continue; }
            for (int j = start; j < i; j++) if (sql.charAt(j) != '\n' && sql.charAt(j) != '\r') out.setCharAt(j, ' ');
        }
        return out.toString();
    }

    static void require(boolean condition) {
        if (!condition) throw new IllegalStateException("Local verification safety prerequisite failed");
    }
}
