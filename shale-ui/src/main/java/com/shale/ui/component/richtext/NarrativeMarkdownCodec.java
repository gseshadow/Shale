package com.shale.ui.component.richtext;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.shale.ui.component.richtext.NarrativeDocument.Format.*;

/** Deterministic codec for plain text, emphasis, Shale's {@code <u>} extension, and Markdown lists. */
public final class NarrativeMarkdownCodec {
    private NarrativeMarkdownCodec() { }

    public static NarrativeDocument decode(String markdown) {
        String source = markdown == null ? "" : markdown;
        StringBuilder text = new StringBuilder();
        List<EnumSet<NarrativeDocument.Format>> styles = new ArrayList<>();
        EnumSet<NarrativeDocument.Format> active = EnumSet.noneOf(NarrativeDocument.Format.class);
        boolean lineStart = true;
        for (int i = 0; i < source.length();) {
            if (lineStart) {
                if (source.startsWith("- ", i)) { append("• ", text, styles, active); i += 2; lineStart = false; continue; }
                int end = orderedMarkerEnd(source, i);
                if (end > i) { append(source.substring(i, end - 1), text, styles, active); append(" ", text, styles, active); i = end; lineStart = false; continue; }
            }
            String token = null; NarrativeDocument.Format format = null;
            if (source.startsWith("**", i) && (active.contains(BOLD) || source.indexOf("**", i + 2) >= 0)) { token = "**"; format = BOLD; }
            else if (source.startsWith("*", i) && (active.contains(ITALIC) || source.indexOf('*', i + 1) >= 0)) { token = "*"; format = ITALIC; }
            else if (source.startsWith("<u>", i)) { token = "<u>"; format = UNDERLINE; }
            else if (source.startsWith("</u>", i) && active.contains(UNDERLINE)) { token = "</u>"; format = UNDERLINE; }
            if (token != null && (format != UNDERLINE || active.contains(UNDERLINE) || source.indexOf("</u>", i + token.length()) >= 0)) {
                if (!active.add(format)) active.remove(format);
                i += token.length(); continue;
            }
            char value = source.charAt(i++); append(String.valueOf(value), text, styles, active); lineStart = value == '\n';
        }
        return new NarrativeDocument(text.toString(), styles);
    }

    public static String encode(NarrativeDocument document) {
        StringBuilder out = new StringBuilder();
        EnumSet<NarrativeDocument.Format> open = EnumSet.noneOf(NarrativeDocument.Format.class);
        String text = document.text();
        for (int i = 0; i <= text.length(); i++) {
            EnumSet<NarrativeDocument.Format> wanted = i == text.length() ? EnumSet.noneOf(NarrativeDocument.Format.class) : document.formatsAt(i);
            close(out, open, wanted, UNDERLINE, "</u>"); close(out, open, wanted, ITALIC, "*"); close(out, open, wanted, BOLD, "**");
            open(out, open, wanted, BOLD, "**"); open(out, open, wanted, ITALIC, "*"); open(out, open, wanted, UNDERLINE, "<u>");
            if (i == text.length()) break;
            if ((i == 0 || text.charAt(i - 1) == '\n') && text.startsWith("• ", i)) { out.append("- "); i++; continue; }
            out.append(text.charAt(i));
        }
        return out.toString();
    }

    public static boolean containsFormatting(String markdown) {
        NarrativeDocument decoded = decode(markdown);
        if (!decoded.text().equals(markdown == null ? "" : markdown)) return true;
        for (int i = 0; i < decoded.text().length(); i++) if (!decoded.formatsAt(i).isEmpty()) return true;
        return false;
    }

    public static String plainText(String markdown) { return decode(markdown).text(); }

    private static int orderedMarkerEnd(String source, int start) {
        int i = start; while (i < source.length() && Character.isDigit(source.charAt(i))) i++;
        return i > start && i + 1 < source.length() && source.charAt(i) == '.' && source.charAt(i + 1) == ' ' ? i + 2 : start;
    }
    private static void append(String value, StringBuilder text, List<EnumSet<NarrativeDocument.Format>> styles, EnumSet<NarrativeDocument.Format> active) {
        text.append(value); for (int j = 0; j < value.length(); j++) styles.add(EnumSet.copyOf(active));
    }
    private static void close(StringBuilder out, EnumSet<NarrativeDocument.Format> open, EnumSet<NarrativeDocument.Format> wanted, NarrativeDocument.Format f, String token) { if (open.contains(f) && !wanted.contains(f)) { out.append(token); open.remove(f); } }
    private static void open(StringBuilder out, EnumSet<NarrativeDocument.Format> open, EnumSet<NarrativeDocument.Format> wanted, NarrativeDocument.Format f, String token) { if (!open.contains(f) && wanted.contains(f)) { out.append(token); open.add(f); } }
}
