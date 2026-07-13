package com.shale.core.model;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CalendarCaseFilterOptions {
    public static final CaseOption ALL_CASES = new CaseOption(null, "All cases");

    private CalendarCaseFilterOptions() {}

    public static List<CaseOption> fromFeedItems(List<CalendarFeedItem> items) {
        Map<Integer, String> caseNamesById = new LinkedHashMap<>();
        if (items != null) {
            for (CalendarFeedItem item : items) {
                if (item == null || item.caseId() == null) continue;
                String caseName = safe(item.caseName()).trim();
                if (caseName.isBlank()) continue;
                caseNamesById.putIfAbsent(item.caseId(), caseName);
            }
        }
        List<CaseOption> caseOptions = caseNamesById.entrySet().stream()
                .map(entry -> new CaseOption(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing((CaseOption option) -> safe(option.displayName()).toLowerCase(Locale.ROOT))
                        .thenComparing(CaseOption::caseId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        java.util.ArrayList<CaseOption> allOptions = new java.util.ArrayList<>();
        allOptions.add(ALL_CASES);
        allOptions.addAll(caseOptions);
        return List.copyOf(allOptions);
    }

    public record CaseOption(Integer caseId, String displayName) {
        public boolean isAll() {
            return caseId == null;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
