package com.shale.ui.component;

/** Transactional draft used by the expanded editor so Cancel cannot leak changes. */
public final class ExpandedTextEdit {
    private final String original;
    private String draft;

    public ExpandedTextEdit(String original) { this.original = original == null ? "" : original; this.draft = this.original; }
    public String original() { return original; }
    public String draft() { return draft; }
    public void setDraft(String draft) { this.draft = draft == null ? "" : draft; }
}
