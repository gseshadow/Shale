package com.shale.server.oauth;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** PR 4C has no live route or activation mode. Reject attempts before singleton startup. */
@Configuration(proxyBeanMethods=false)
public class CimdRuntimeConfiguration {
    @Bean static BeanFactoryPostProcessor cimdActivationFence(Environment environment) {
        return factory -> {
            for(String key:java.util.List.of("shale.ai.cimd.enabled","shale.ai.cimd.composition-enabled")) {
                String value=environment.getProperty(key);
                if(value!=null && !"false".equalsIgnoreCase(value))
                    throw new IllegalStateException("CIMD HTTP composition is unavailable; dedicated runtime verification does not authorize activation.");
            }
        };
    }
}
