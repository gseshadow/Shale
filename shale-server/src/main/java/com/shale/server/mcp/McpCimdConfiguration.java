package com.shale.server.mcp;

import com.shale.data.security.ai.AiCimdCaseData;
import com.shale.server.oauth.McpOAuthProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** PR A Case composition through the existing predecessor verification; no alternate pool manager. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="shale.ai.cimd.enabled",havingValue="true")
public class McpCimdConfiguration {
    @Bean(destroyMethod="close") AiCimdCaseData cimdCaseData(McpAiCaseData predecessor,CimdCaseConnections connections,
                                                          McpOAuthProperties identity,Environment env) {
        return new AiCimdCaseData(predecessor::attestation,connections.data(),connections.reviewer(),
                env.getRequiredProperty("shale.ai.data.database"),identity.issuer(),identity.resource(),env.getProperty("shale.ai.dcr.enabled",Boolean.class,false)?com.shale.data.security.ai.AiExternalContract.DCR_V3:com.shale.data.security.ai.AiExternalContract.CIMD_V1);
    }
}
