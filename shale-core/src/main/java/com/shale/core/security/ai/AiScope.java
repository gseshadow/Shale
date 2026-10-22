package com.shale.core.security.ai;

import java.util.EnumSet;
import java.util.Set;

/** Closed pilot vocabulary. These scopes only reduce ordinary Shale authority. */
public enum AiScope {
    CASES_SEARCH("cases.search", 1), CASES_OVERVIEW_READ("cases.overview.read", 2);

    private final String wireValue;
    private final int bit;
    AiScope(String wireValue, int bit) { this.wireValue = wireValue; this.bit = bit; }
    public String wireValue() { return wireValue; }
    public int bit() { return bit; }

    public static AiScope parse(String value) {
        for (AiScope scope : values()) if (scope.wireValue.equals(value)) return scope;
        throw new IllegalArgumentException("Unsupported AI scope.");
    }

    public static int mask(Set<AiScope> scopes) {
        if (scopes == null) throw new IllegalArgumentException("AI scopes are required.");
        int mask = 0;
        for (AiScope scope : scopes) mask |= java.util.Objects.requireNonNull(scope).bit;
        return mask;
    }

    public static Set<AiScope> fromMask(int mask) {
        if (mask < 0 || mask > 3) throw new IllegalArgumentException("Invalid AI scope set.");
        EnumSet<AiScope> result = EnumSet.noneOf(AiScope.class);
        for (AiScope scope : values()) if ((mask & scope.bit) != 0) result.add(scope);
        return Set.copyOf(result);
    }

    public static Set<AiScope> intersect(Set<AiScope> ceiling, Set<AiScope> consent, Set<AiScope> session) {
        return fromMask(mask(ceiling) & mask(consent) & mask(session));
    }
}
