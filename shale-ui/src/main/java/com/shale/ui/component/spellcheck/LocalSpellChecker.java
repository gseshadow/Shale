package com.shale.ui.component.spellcheck;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A deliberately local, dependency-free dictionary spell checker. */
public final class LocalSpellChecker {
    public record Misspelling(int start, int end, String word) { }
    private static final Pattern WORD = Pattern.compile("[\\p{L}][\\p{L}'’-]*");
    private final Set<String> dictionary = new LinkedHashSet<>();
    private final Set<String> ignored = new LinkedHashSet<>();
    private final Set<String> custom = new LinkedHashSet<>();

    public LocalSpellChecker(Collection<String> words) { addNormalized(dictionary, words); }

    public boolean isMisspelled(String word) {
        String normalized = normalize(word);
        return normalized.length() > 1 && !dictionary.contains(normalized)
                && !custom.contains(normalized) && !ignored.contains(normalized);
    }

    public List<String> misspellings(String text) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Matcher matcher = WORD.matcher(text == null ? "" : text);
        while (matcher.find()) if (isMisspelled(matcher.group())) result.add(matcher.group());
        return List.copyOf(result);
    }

    public List<Misspelling> misspellingRanges(String text) {
        List<Misspelling> result = new java.util.ArrayList<>();
        Matcher matcher = WORD.matcher(text == null ? "" : text);
        while (matcher.find()) if (isMisspelled(matcher.group())) result.add(new Misspelling(matcher.start(), matcher.end(), matcher.group()));
        return List.copyOf(result);
    }

    public List<String> suggestions(String word, int limit) {
        String target = normalize(word);
        return java.util.stream.Stream.concat(dictionary.stream(), custom.stream())
                .filter(candidate -> Math.abs(candidate.length() - target.length()) <= 2)
                .sorted(java.util.Comparator.comparingInt(candidate -> distance(target, candidate)))
                .limit(Math.max(0, limit)).toList();
    }

    public void ignore(String word) { ignored.add(normalize(word)); }
    public void addToCustomDictionary(String word) { custom.add(normalize(word)); }
    public Set<String> customDictionary() { return Set.copyOf(custom); }

    private static void addNormalized(Set<String> target, Collection<String> words) {
        if (words != null) words.stream().map(LocalSpellChecker::normalize).filter(s -> !s.isBlank()).forEach(target::add);
    }

    private static String normalize(String word) { return word == null ? "" : word.toLowerCase(Locale.ROOT); }

    static int distance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1]; current[0] = i;
            for (int j = 1; j <= right.length(); j++) current[j] = Math.min(Math.min(
                    current[j - 1] + 1, previous[j] + 1), previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1));
            previous = current;
        }
        return previous[right.length()];
    }
}
