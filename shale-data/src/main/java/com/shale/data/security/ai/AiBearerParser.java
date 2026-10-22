package com.shale.data.security.ai;

import java.util.List;

/** Future header extraction seam. No servlet/filter/route registration or alternative credential source. */
public final class AiBearerParser {
    private AiBearerParser() {}
    /** Pass all Authorization field values; callers must reject token query/cookie/alternate sources.
     * Exactly one ASCII space and the issued opaque alphabet/length are accepted. */
    public static String extract(List<String> authorizationHeaders) {
        if(authorizationHeaders==null || authorizationHeaders.size()!=1) throw denied();
        String value=authorizationHeaders.getFirst();
        if(value==null || value.length()!=50 || !value.regionMatches(true,0,"Bearer ",0,7)
                || !value.substring(7).matches("[A-Za-z0-9_-]{43}")) throw denied();
        return value.substring(7);
    }
    private static SecurityException denied() { return new SecurityException("Integration authentication failed."); }
}
