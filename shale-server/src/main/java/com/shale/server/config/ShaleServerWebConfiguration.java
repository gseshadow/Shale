package com.shale.server.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ShaleServerWebConfiguration implements WebMvcConfigurer {
    private static final String PRIMARY_ALLOWED_ORIGINS = "SHALE_ALLOWED_CORS_ORIGINS";
    private static final String SERVER_ALLOWED_ORIGINS = "SHALE_SERVER_ALLOWED_CORS_ORIGINS";

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> allowedOrigins = allowedOrigins();
        if (allowedOrigins.isEmpty()) {
            return;
        }

        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }

    private static List<String> allowedOrigins() {
        String configured = firstNonBlank(System.getenv(PRIMARY_ALLOWED_ORIGINS), System.getProperty(PRIMARY_ALLOWED_ORIGINS),
                System.getenv(SERVER_ALLOWED_ORIGINS), System.getProperty(SERVER_ALLOWED_ORIGINS));
        if (configured == null) {
            return List.of();
        }
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .distinct()
                .toList();
    }

    private static String firstNonBlank(String... values) {
        return Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
