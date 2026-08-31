package com.shale.ui.component.richtext;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Toolkit-independent narrative text plus Shale's deliberately small style vocabulary. */
public final class NarrativeDocument {
    public enum Format { BOLD, ITALIC, UNDERLINE }

    private final String text;
    private final List<EnumSet<Format>> formats;

    public NarrativeDocument(String text, List<EnumSet<Format>> formats) {
        this.text = text == null ? "" : text;
        if (formats.size() != this.text.length()) throw new IllegalArgumentException("One format entry is required per character");
        this.formats = formats.stream().map(EnumSet::copyOf).toList();
    }

    public static NarrativeDocument plain(String text) {
        String value = text == null ? "" : text;
        List<EnumSet<Format>> styles = new ArrayList<>(value.length());
        for (int i = 0; i < value.length(); i++) styles.add(EnumSet.noneOf(Format.class));
        return new NarrativeDocument(value, styles);
    }

    public String text() { return text; }
    public EnumSet<Format> formatsAt(int index) { return EnumSet.copyOf(formats.get(index)); }
    public boolean has(Format format, int index) { return formats.get(index).contains(format); }
}
