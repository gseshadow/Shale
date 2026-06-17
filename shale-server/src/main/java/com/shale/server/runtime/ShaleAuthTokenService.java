package com.shale.server.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class ShaleAuthTokenService {
    public static final String SECRET_ENV = "SHALE_AUTH_TOKEN_SECRET";
    public static final String TTL_SECONDS_ENV = "SHALE_AUTH_TOKEN_TTL_SECONDS";
    private static final long DEFAULT_TTL_SECONDS = 8 * 60 * 60;
    private static final int MIN_SECRET_LENGTH = 32;
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final Pattern INT_PATTERN_TEMPLATE = Pattern.compile("\"%s\":(\\d+)");
    private static final Pattern STRING_PATTERN_TEMPLATE = Pattern.compile("\"%s\":\"([^\"]*)\"");

    private final byte[] secret;
    private final long ttlSeconds;
    private final Clock clock;
    private final boolean enabled;

    private ShaleAuthTokenService(Clock clock) {
        this.secret = new byte[0];
        this.ttlSeconds = 0;
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.enabled = false;
    }

    public ShaleAuthTokenService(String secret, long ttlSeconds, Clock clock) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Missing required config: " + SECRET_ENV);
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(SECRET_ENV + " must be at least " + MIN_SECRET_LENGTH + " characters.");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalStateException(TTL_SECONDS_ENV + " must be greater than zero.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.enabled = true;
    }

    public static ShaleAuthTokenService disabled() {
        return new ShaleAuthTokenService(Clock.systemUTC());
    }

    public static ShaleAuthTokenService fromEnvironment() {
        return new ShaleAuthTokenService(must(SECRET_ENV), longVal(TTL_SECONDS_ENV, DEFAULT_TTL_SECONDS), Clock.systemUTC());
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    public String issue(ServerPrincipal principal) {
        if (!enabled) {
            throw new IllegalStateException("Authentication tokens are not enabled for this profile.");
        }
        java.util.Objects.requireNonNull(principal, "principal");
        long issuedAt = Instant.now(clock).getEpochSecond();
        long expiresAt = issuedAt + ttlSeconds;
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        StringBuilder payload = new StringBuilder()
                .append("{\"sub\":").append(principal.userId())
                .append(",\"userId\":").append(principal.userId())
                .append(",\"shaleClientId\":").append(principal.shaleClientId())
                .append(",\"iat\":").append(issuedAt)
                .append(",\"exp\":").append(expiresAt);
        if (principal.email() != null && !principal.email().isBlank()) {
            payload.append(",\"email\":\"").append(jsonEscape(principal.email())).append("\"");
        }
        payload.append('}');
        String signingInput = encode(header.getBytes(StandardCharsets.UTF_8)) + "." + encode(payload.toString().getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + encode(sign(signingInput));
    }

    public Optional<ServerPrincipal> verify(String token) {
        if (!enabled) {
            return Optional.empty();
        }
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            return Optional.empty();
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = sign(signingInput);
        byte[] actual;
        try {
            actual = URL_DECODER.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            return Optional.empty();
        }
        String payload;
        try {
            payload = new String(URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        Integer userId = intClaim(payload, "userId");
        Integer shaleClientId = intClaim(payload, "shaleClientId");
        Long exp = longClaim(payload, "exp");
        if (userId == null || shaleClientId == null || exp == null || exp <= Instant.now(clock).getEpochSecond()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ServerPrincipal(userId, shaleClientId, stringClaim(payload, "email")));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA256));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Unable to sign auth token.", e);
        }
    }

    private static String encode(byte[] bytes) {
        return URL_ENCODER.encodeToString(bytes);
    }

    private static Integer intClaim(String payload, String name) {
        Long value = longClaim(payload, name);
        return value == null || value > Integer.MAX_VALUE ? null : value.intValue();
    }

    private static Long longClaim(String payload, String name) {
        Matcher matcher = Pattern.compile(INT_PATTERN_TEMPLATE.pattern().formatted(Pattern.quote(name))).matcher(payload);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private static String stringClaim(String payload, String name) {
        Matcher matcher = Pattern.compile(STRING_PATTERN_TEMPLATE.pattern().formatted(Pattern.quote(name))).matcher(payload);
        return matcher.find() ? matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : null;
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String must(String key) {
        String value = System.getenv(key);
        if (value == null) {
            value = System.getProperty(key);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required config: " + key);
        }
        return value;
    }

    private static long longVal(String key, long defaultValue) {
        String value = System.getenv(key);
        if (value == null) {
            value = System.getProperty(key);
        }
        return value == null || value.isBlank() ? defaultValue : Long.parseLong(value.trim());
    }
}
