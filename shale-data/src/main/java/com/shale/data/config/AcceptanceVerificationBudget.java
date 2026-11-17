package com.shale.data.config;

import java.util.Map;

/** Longer, still bounded verification on the dedicated acceptance App Service only.
 * No authority, result, expiry, quota, transaction or feature switch is changed. */
public final class AcceptanceVerificationBudget {
    private AcceptanceVerificationBudget() {}
    private static final boolean ACTIVE=enabled(System.getenv());
    static boolean enabled(Map<String,String> environment) {
        String flag=environment.get("SHALE_AI_ACCEPTANCE_SLOWVERIFICATION");
        if(flag==null||flag.equals("false"))return false;
        if(!flag.equals("true")
                ||!"shale-mcp-acceptance".equals(environment.get("WEBSITE_SITE_NAME"))
                ||!"ShaleMcpAcceptance".equals(environment.get("SHALE_AI_DATA_DATABASE"))
                ||!"https://shale-mcp-acceptance.azurewebsites.net".equals(environment.get("SHALE_AI_OAUTH_PUBLICORIGIN"))
                ||!"PRODUCTION".equals(environment.get("SHALE_AI_OAUTH_IDENTITY"))
                ||!"azure".equals(environment.get("SPRING_PROFILES_ACTIVE")))
            throw new IllegalStateException("Acceptance verification budget target refused.");
        for(String key:sourceKeys()) {
            String url=environment.get(key);
            if(url==null||!url.matches("jdbc:sqlserver://shale\\.database\\.windows\\.net(?::1433)?;databaseName=ShaleMcpAcceptance;?"))
                throw new IllegalStateException("Acceptance verification budget datasource refused.");
        }
        return true;
    }
    static java.util.List<String> sourceKeys() {
        var keys=new java.util.ArrayList<>(java.util.List.of("SHALE_APP_DB_URL","SHALE_RT_DB_URL",
                "SHALE_AI_DATA_JDBCURL","SHALE_AI_DATA_VERIFIER_JDBCURL","SHALE_AI_OAUTH_BOOTSTRAP_JDBCURL"));
        for(String role:java.util.List.of("CASE","REVIEWER","PUBLISHER","APPROVAL","ONBOARDING","BROWSER","ISSUER",
                "METADATA","RESOLVER","NEUTRAL","DCRENDPOINT","DCRMETADATA"))
            keys.add("SHALE_AI_CIMD_RUNTIME_"+role+"_JDBCURL");
        return java.util.List.copyOf(keys);
    }
    public static long operationNanos(long normal) { return ACTIVE?600_000_000_000L:normal; }
    public static int operationMillis(int normal) { return ACTIVE?600_000:normal; }
    public static int networkMillis(int normal) { return ACTIVE?180_000:normal; }
    public static int querySeconds(int normal) { return ACTIVE?180:normal; }
    public static int loginSeconds(int normal) { return ACTIVE?30:normal; }
}
