package com.shale.server.oauth;

import java.net.URI;
import java.util.Set;

/** Phase 3A exact hosted redirects; only a native public HTTP loopback port may vary. */
public final class OAuthRedirectPolicy {
    private OAuthRedirectPolicy() {}

    public static URI registered(String value, boolean confidential) {
        if (value == null || value.length() > 2048) throw invalid();
        URI uri;
        try { uri = URI.create(value); } catch (IllegalArgumentException failure) { throw invalid(); }
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null ||
                uri.getRawFragment() != null || uri.getRawPath().isEmpty() ||
                !uri.normalize().equals(uri) || value.contains("*") || value.contains("%") ||
                uri.getPort() == 0 || uri.getPort() > 65535 ||
                !("https".equals(uri.getScheme()) || (!confidential && nativeLoopback(uri)))) throw invalid();
        return uri;
    }

    public static void validate(String requested, Set<String> registered, boolean confidential) {
        URI target = registered(requested, confidential);
        for (String value : registered) {
            URI approved = registered(value, confidential);
            if (value.equals(requested)) return;
            if (!confidential && nativeLoopback(approved) && nativeLoopback(target) &&
                    approved.getHost().equals(target.getHost()) &&
                    approved.getRawPath().equals(target.getRawPath())) return;
        }
        throw invalid();
    }

    private static boolean nativeLoopback(URI uri) {
        return "http".equals(uri.getScheme()) && uri.getPort() > 0 &&
                ("127.0.0.1".equals(uri.getHost()) || "[::1]".equals(uri.getHost()));
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("An approved exact OAuth redirect is required.");
    }
}
