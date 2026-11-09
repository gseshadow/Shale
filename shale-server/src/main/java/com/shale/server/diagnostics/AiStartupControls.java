package com.shale.server.diagnostics;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.core.env.Environment;

/** Closed, nonsecret vocabulary shared only by startup observations. Never grants feature authority. */
public final class AiStartupControls {
    private AiStartupControls() { }

    public enum Control {
        AI("shale.ai.enabled"),
        MCP("shale.ai.mcp.enabled"),
        CASE_DATA("shale.ai.mcp.case-data-composition-enabled"),
        SEARCH("shale.ai.mcp.search-cases-enabled"),
        OVERVIEW("shale.ai.mcp.get-case-overview-enabled"),
        OAUTH_COMPOSITION("shale.ai.oauth.composition-enabled"),
        OAUTH_AUTHORIZATION("shale.ai.oauth.authorization-enabled"),
        OAUTH_EXCHANGE("shale.ai.oauth.exchange-enabled");

        private final String key;
        Control(String key) { this.key = key; }
        public String key() { return key; }
    }

    public enum Value {
        TRUE, FALSE, UNKNOWN;

        public static Value of(boolean value) { return value ? TRUE : FALSE; }
        public Value agree(Value other) { return this == other ? this : UNKNOWN; }
        public Value and(Value other) {
            return this == FALSE || other == FALSE ? FALSE
                    : this == TRUE && other == TRUE ? TRUE : UNKNOWN;
        }
        public Value or(Value other) {
            return this == TRUE || other == TRUE ? TRUE
                    : this == FALSE && other == FALSE ? FALSE : UNKNOWN;
        }
    }

    public static Map<Control, Value> read(Environment environment, Control... controls) {
        var result = new EnumMap<Control, Value>(Control.class);
        for (Control control : controls) {
            Value value = Value.UNKNOWN;
            try {
                // Resolve only the allowlisted key through Spring's effective precedence/placeholders.
                // Missing, nonliteral, malformed or inaccessible values are never a false default.
                String raw = environment.getProperty(control.key());
                if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) {
                    Boolean consumed = environment.getProperty(control.key(), Boolean.class);
                    if (consumed != null) value = Value.of(consumed)
                            .agree(Value.of("true".equalsIgnoreCase(raw)));
                }
            } catch (RuntimeException unavailable) {
                // Neither raw values nor exception messages may enter operational evidence.
            }
            result.put(control, value);
        }
        return result;
    }
}
