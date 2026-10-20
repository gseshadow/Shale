package com.shale.core.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Bounded, case-specific CaseUpdates read contract; all filters execute in SQL. */
public record CaseUpdateQuery(long caseId, int shaleClientId, int actorUserId, String text,
        Sort sort, LocalDate from, LocalDate through, Integer authorUserId,
        Visibility visibility, Cursor after, int pageSize, java.time.ZoneId dateZone) {
    public enum Sort {
        NEWEST("Newest first"), OLDEST("Oldest first"), EDITED("Recently edited");
        private final String label;
        Sort(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public enum Visibility {
        ACTIVE("Active"), DELETED("Deleted"), ALL("Active and deleted");
        private final String label;
        Visibility(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public record Cursor(LocalDateTime at, long id) {
        public Cursor {
            if (at == null || id <= 0) throw new IllegalArgumentException("A timestamp and update identity are required.");
        }
    }
    public CaseUpdateQuery(long caseId, int shaleClientId, int actorUserId, String text,
            Sort sort, LocalDate from, LocalDate through, Integer authorUserId,
            Visibility visibility, Cursor after, int pageSize) {
        this(caseId, shaleClientId, actorUserId, text, sort, from, through, authorUserId, visibility, after, pageSize, java.time.ZoneOffset.UTC);
    }
    public CaseUpdateQuery inZone(java.time.ZoneId zone) {
        return new CaseUpdateQuery(caseId, shaleClientId, actorUserId, text, sort, from, through,
                authorUserId, visibility, after, pageSize, zone);
    }
    public LocalDateTime fromUtc() { return from == null ? null : utcMidnight(from); }
    public LocalDateTime throughExclusiveUtc() { return through == null ? null : utcMidnight(through.plusDays(1)); }
    private LocalDateTime utcMidnight(LocalDate date) {
        return date.atStartOfDay(dateZone).withZoneSameInstant(java.time.ZoneOffset.UTC).toLocalDateTime();
    }
    public CaseUpdateQuery {
        if (caseId <= 0 || shaleClientId <= 0 || actorUserId <= 0)
            throw new IllegalArgumentException("Case, tenant and actor are required.");
        dateZone = dateZone == null ? java.time.ZoneOffset.UTC : dateZone;
        text = text == null ? "" : text.trim();
        if (text.length() > 1000) throw new IllegalArgumentException("Search must be at most 1000 characters.");
        sort = sort == null ? Sort.NEWEST : sort;
        visibility = visibility == null ? Visibility.ACTIVE : visibility;
        if (from != null && through != null && from.isAfter(through))
            throw new IllegalArgumentException("From date must be on or before through date.");
        if (through != null && through.equals(LocalDate.MAX))
            throw new IllegalArgumentException("Through date is outside the supported range.");
        if (authorUserId != null && authorUserId <= 0) throw new IllegalArgumentException("Author identity must be positive.");
        if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("Page size must be between 1 and 100.");
    }
    public static CaseUpdateQuery newest(long caseId, int tenant, int actor, String text, int size) {
        return new CaseUpdateQuery(caseId, tenant, actor, text, Sort.NEWEST, null, null, null, Visibility.ACTIVE, null, size);
    }
}
