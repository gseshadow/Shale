package com.shale.server.oauth;

import java.net.URI;
import java.util.Set;

/** Phase 3A exact hosted redirects; only a native public HTTP loopback port may vary. */
public final class OAuthRedirectPolicy {
    private OAuthRedirectPolicy() {}

    public static URI registered(String value, boolean confidential) { return registered(value,confidential,false); }
    static URI registeredDcr(String value,boolean confidential) {return registered(value,confidential,true);}
    private static URI registered(String value, boolean confidential,boolean dcr) {
        if (value == null || value.length() > 2048) throw invalid();
        URI uri;
        try { uri = URI.create(value); } catch (IllegalArgumentException failure) { throw invalid(); }
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null ||
                uri.getRawFragment() != null || uri.getRawPath().isEmpty() ||
                !uri.normalize().equals(uri) || value.contains("*") || value.contains("%") ||
                uri.getPort() == 0 || uri.getPort() > 65535 ||
                !("https".equals(uri.getScheme()) || (!confidential && (nativeLoopback(uri)||(dcr&&"http".equals(uri.getScheme())&&"localhost".equals(uri.getHost())&&uri.getPort()>0))))) throw invalid();
        return uri;
    }

    public static void validate(String requested, Set<String> registered, boolean confidential) {
        validate(requested,registered,confidential,null);
    }
    public static void validate(String requested,Set<String> registered,boolean confidential,String clientId) {
        boolean dcr=clientId!=null&&clientId.matches("dcr\\.[0-9a-f]{64}");
        URI target = registered(requested, confidential,dcr);
        for (String value : registered) {
            URI approved = registered(value, confidential,dcr);
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
