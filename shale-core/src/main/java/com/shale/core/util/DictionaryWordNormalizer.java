package com.shale.core.util;

import java.util.Locale;

/** Canonical identity contract shared by spellchecking and dictionary persistence. */
public final class DictionaryWordNormalizer {
    private DictionaryWordNormalizer() { }

    public static String normalize(String word) {
        return word == null ? "" : word.strip().replace("\uFEFF", "")
                .replace('\u2018', '\'').replace('\u2019', '\'').toLowerCase(Locale.ROOT);
    }
}
