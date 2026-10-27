package com.shale.server.mcp;

import com.shale.data.config.DataSources;
import com.shale.data.security.ai.*;
import com.shale.server.oauth.McpOAuthProperties;
import org.springframework.core.env.Environment;
import javax.sql.DataSource;
import java.util.List;

/** Enabled Case composition has one independent AI pool and a live, separately governed catalog verifier. */
final class McpAiCaseData {
    private final DataSource data,verifier;
    private final List<DataSource> forbidden;
    private final String database;
    private final McpOAuthProperties identity;
    McpAiCaseData(Environment env,McpOAuthProperties identity,DataSources sources) {
        this(configuredData(env,sources),source(env,"shale.ai.data.verifier"),
                List.of(sources.auth(),sources.runtime(),source(env,"shale.ai.oauth.bootstrap")),
                env.getRequiredProperty("shale.ai.data.database"),identity);
    }
    McpAiCaseData(DataSource data,DataSource verifier,List<DataSource> forbidden,String database,McpOAuthProperties identity) {
        this.data=java.util.Objects.requireNonNull(data);this.verifier=java.util.Objects.requireNonNull(verifier);
        this.forbidden=List.copyOf(forbidden);this.database=java.util.Objects.requireNonNull(database);this.identity=java.util.Objects.requireNonNull(identity);
        McpPayloadLogFilter.installSqlProtection();
        ready(System.nanoTime()+10_000_000_000L);
    }
    void ready(long deadline) {
        new AiCaseDataAttestation(new McpJdbcDeadline(data,deadline),new McpJdbcDeadline(verifier,deadline),
                forbidden.stream().map(d->(DataSource)new McpJdbcDeadline(d,deadline)).toList(),database,identity.issuer(),identity.resource(),
                identity.identity()==McpOAuthProperties.Identity.PRODUCTION).verify();
    }
    AiCaseDataRequest authenticate(String token,long deadline) {
        ready(deadline);
        return AiCaseDataAccess.authenticate(new McpJdbcDeadline(data,deadline),token,identity.issuer(),identity.resource(),deadline);
    }
    IntegrationCaseReader reader(long deadline,AiCaseDataRequest request) {
        return new AiCaseDataAccess(new McpJdbcDeadline(data,deadline),until->new McpJdbcDeadline(data,until),request);
    }
    private static DataSource configuredData(Environment env,DataSources sources) {
        // Resolve every dedicated setting before allocating the independent pool.
        var config=new com.shale.data.config.Config(env::getProperty);
        config.aiCaseDataJdbcUrl();config.aiCaseDataUser();config.aiCaseDataPassword();
        for(String prefix:List.of("shale.ai.data.verifier","shale.ai.oauth.bootstrap"))
            for(String suffix:List.of(".jdbc-url",".username",".password"))required(env,prefix+suffix);
        required(env,"shale.ai.data.database");
        return sources.aiCaseData();
    }
    private static String required(Environment env,String key) {
        String value=env.getRequiredProperty(key);
        if(value.isBlank())throw new IllegalStateException("Incomplete AI Case datasource configuration.");
        return value;
    }
    private static DataSource source(Environment env,String prefix) {
        var source=new com.microsoft.sqlserver.jdbc.SQLServerDataSource();
        String url=required(env,prefix+".jdbc-url");
        if(!url.startsWith("jdbc:sqlserver://")||java.util.regex.Pattern.compile("(?i);\\s*(encrypt|trustServerCertificate|hostNameInCertificate|user|userName|password|integratedSecurity|authentication|accessToken|multipleActiveResultSets|loginTimeout|socketTimeout)\\s*=").matcher(url).find())
            throw new IllegalStateException("AI JDBC URL contains a protected connection setting.");
        source.setURL(url);source.setUser(required(env,prefix+".username"));source.setPassword(required(env,prefix+".password"));
        source.setLoginTimeout(2);source.setSocketTimeout(5000);source.setEncrypt("true");source.setTrustServerCertificate(false);
        return source;
    }
}
