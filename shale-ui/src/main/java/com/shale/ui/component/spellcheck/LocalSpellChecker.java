package com.shale.ui.component.spellcheck;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A deliberately local, dependency-free, layered dictionary spell checker. */
public final class LocalSpellChecker {
    public record Misspelling(int start, int end, String word) { }
    private static final Pattern WORD = Pattern.compile("[\\p{L}][\\p{L}'’-]*");
    private static final Pattern NON_WORD = Pattern.compile(
            "(?i)\\b(?:https?://|www\\.)\\S+|\\b[\\w.+-]+@[\\w.-]+\\.[\\p{L}]{2,}\\b"
                    + "|\\b\\d+(?:[/:.-]\\d+)+\\b|\\b\\d+\\b"
                    + "|\\b(?=[\\p{L}\\d-]*\\d)[\\p{L}\\d]+(?:-[\\p{L}\\d]+)+\\b");
    private final Set<String> dictionary = new LinkedHashSet<>();
    private final Set<String> ignored = new LinkedHashSet<>();
    private final Set<String> custom = new LinkedHashSet<>();
    private final Map<Integer, Set<String>> suggestionsByLength = new HashMap<>();

    public LocalSpellChecker(Collection<String> words) { addWords(words); }

    /** Adds another application dictionary layer (for example legal or medical terms). */
    public void addDictionaryLayer(Collection<String> words) { addWords(words); }

    public boolean isMisspelled(String word) {
        String normalized = normalize(word);
        return normalized.length() > 1 && !dictionary.contains(normalized)
                && !custom.contains(normalized) && !ignored.contains(normalized);
    }

    public List<String> misspellings(String text) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        misspellingRanges(text).forEach(misspelling -> result.add(misspelling.word()));
        return List.copyOf(result);
    }

    public List<Misspelling> misspellingRanges(String text) {
        List<Misspelling> result = new java.util.ArrayList<>();
        String source = text == null ? "" : text;
        List<int[]> excluded = excludedRanges(source);
        Matcher matcher = WORD.matcher(source);
        while (matcher.find()) if (!overlaps(excluded, matcher.start(), matcher.end()) && isMisspelled(matcher.group()))
            result.add(new Misspelling(matcher.start(), matcher.end(), matcher.group()));
        return List.copyOf(result);
    }

    public List<String> suggestions(String word, int limit) {
        String target = normalize(word);
        return java.util.stream.IntStream.rangeClosed(Math.max(1, target.length() - 2), target.length() + 2)
                .mapToObj(length -> suggestionsByLength.getOrDefault(length, Set.of()).stream())
                .flatMap(stream -> stream)
                .filter(candidate -> target.isEmpty() || candidate.charAt(0) == target.charAt(0))
                .sorted(java.util.Comparator.comparingInt(candidate -> distance(target, candidate)))
                .limit(Math.max(0, limit)).toList();
    }

    public void ignore(String word) { ignored.add(normalize(word)); }
    public void addToCustomDictionary(String word) {
        String normalized = normalize(word);
        if (!normalized.isBlank() && custom.add(normalized)) index(normalized);
    }
    public Set<String> customDictionary() { return Set.copyOf(custom); }

    private static void addNormalized(Set<String> target, Collection<String> words) {
        if (words != null) words.stream().map(LocalSpellChecker::normalize).filter(s -> !s.isBlank()).forEach(target::add);
    }

    private void addWords(Collection<String> words) {
        int previousSize = dictionary.size();
        addNormalized(dictionary, words);
        if (dictionary.size() != previousSize) dictionary.forEach(this::index);
    }

    private void index(String word) {
        suggestionsByLength.computeIfAbsent(word.length(), ignored -> new LinkedHashSet<>()).add(word);
    }

    static String normalize(String word) {
        return word == null ? "" : word.strip().replace("\uFEFF", "")
                .replace('\u2019', '\'').toLowerCase(Locale.ROOT);
    }

    private static List<int[]> excludedRanges(String text) {
        List<int[]> ranges = new java.util.ArrayList<>();
        Matcher matcher = NON_WORD.matcher(text);
        while (matcher.find()) ranges.add(new int[]{matcher.start(), matcher.end()});
        return ranges;
    }

    private static boolean overlaps(List<int[]> ranges, int start, int end) {
        return ranges.stream().anyMatch(range -> start < range[1] && end > range[0]);
    }

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
