package com.shale.ui.component.spellcheck;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable, offline Hunspell dictionary compiled from a bundled .dic/.aff pair.
 *
 * <p>The English affix file uses the standard single-character flag format. Rules
 * are expanded once, including permitted prefix/suffix cross-products, so checking
 * is a set lookup and opening a spelling menu never walks the complete dictionary.
 */
final class HunspellDictionary {
    private record Rule(boolean prefix, char flag, boolean crossProduct, String strip,
                        String add, Pattern condition) {
        String apply(String stem) {
            if (!condition.matcher(stem).find()) return null;
            if (prefix) {
                if (!strip.isEmpty() && !stem.startsWith(strip)) return null;
                return add + stem.substring(strip.length());
            }
            if (!strip.isEmpty() && !stem.endsWith(strip)) return null;
            return stem.substring(0, stem.length() - strip.length()) + add;
        }
    }

    private final Set<String> words;
    private final Map<Integer, Set<String>> wordsByLength;
    private final List<String[]> replacements;

    private HunspellDictionary(Set<String> words, List<String[]> replacements) {
        this.words = Set.copyOf(words);
        this.replacements = List.copyOf(replacements);
        Map<Integer, Set<String>> index = new HashMap<>();
        words.forEach(word -> index.computeIfAbsent(word.length(), ignored -> new LinkedHashSet<>()).add(word));
        this.wordsByLength = Map.copyOf(index);
    }

    static HunspellDictionary load(String dictionaryResource, String affixResource) {
        try {
            AffixData affix = readAffix(affixResource);
            LinkedHashSet<String> expanded = new LinkedHashSet<>();
            try (var stream = requiredResource(dictionaryResource);
                 var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                boolean first = true;
                for (String line; (line = reader.readLine()) != null; ) {
                    String entry = line.strip().replace("\uFEFF", "");
                    if (entry.isEmpty()) continue;
                    if (first && entry.chars().allMatch(Character::isDigit)) { first = false; continue; }
                    first = false;
                    int tab = entry.indexOf('\t');
                    if (tab >= 0) entry = entry.substring(0, tab);
                    int slash = unescapedSlash(entry);
                    String stem = slash < 0 ? entry : entry.substring(0, slash).replace("\\/", "/");
                    String flags = slash < 0 ? "" : entry.substring(slash + 1);
                    addForms(expanded, LocalSpellChecker.normalize(stem), flags, affix.rules());
                }
            }
            return new HunspellDictionary(expanded, affix.replacements());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load bundled Hunspell resources", exception);
        }
    }

    boolean contains(String word) { return words.contains(word); }
    Set<String> words() { return words; }

    List<String> suggestions(String input, int limit) {
        if (limit <= 0 || input.isEmpty()) return List.of();
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String[] replacement : replacements) {
            int at = input.indexOf(replacement[0]);
            if (at >= 0) candidates.add(input.substring(0, at) + replacement[1]
                    + input.substring(at + replacement[0].length()));
        }
        for (int length = Math.max(1, input.length() - 2); length <= input.length() + 2; length++)
            candidates.addAll(wordsByLength.getOrDefault(length, Set.of()));
        return candidates.stream().filter(words::contains)
                .filter(candidate -> candidate.charAt(0) == input.charAt(0))
                .sorted(Comparator.comparingInt((String word) -> LocalSpellChecker.distance(input, word))
                        .thenComparing(String::compareTo))
                .limit(limit).toList();
    }

    private static void addForms(Set<String> words, String stem, String flags, Map<Character, List<Rule>> rules) {
        words.add(stem);
        List<Rule> prefixes = new ArrayList<>(), suffixes = new ArrayList<>();
        flags.chars().mapToObj(value -> rules.getOrDefault((char) value, List.of())).flatMap(List::stream)
                .forEach(rule -> (rule.prefix ? prefixes : suffixes).add(rule));
        for (Rule rule : prefixes) add(words, rule.apply(stem));
        for (Rule rule : suffixes) add(words, rule.apply(stem));
        for (Rule prefix : prefixes) for (Rule suffix : suffixes)
            if (prefix.crossProduct && suffix.crossProduct) {
                String prefixed = prefix.apply(stem);
                if (prefixed != null) add(words, suffix.apply(prefixed));
            }
    }

    private static void add(Set<String> words, String word) { if (word != null) words.add(LocalSpellChecker.normalize(word)); }
    private record AffixData(Map<Character, List<Rule>> rules, List<String[]> replacements) { }

    private static AffixData readAffix(String resource) throws IOException {
        Map<Character, Boolean> cross = new HashMap<>();
        Map<Character, List<Rule>> rules = new HashMap<>();
        List<String[]> replacements = new ArrayList<>();
        try (var stream = requiredResource(resource);
             var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                String value = line.strip();
                if (value.isEmpty() || value.startsWith("#")) continue;
                String[] part = value.split("\\s+");
                if ((part[0].equals("PFX") || part[0].equals("SFX")) && part.length == 4) {
                    cross.put(part[1].charAt(0), part[2].equals("Y"));
                } else if ((part[0].equals("PFX") || part[0].equals("SFX")) && part.length >= 5) {
                    boolean prefix = part[0].equals("PFX");
                    char flag = part[1].charAt(0);
                    String strip = zero(part[2]);
                    String add = zero(part[3].split("/", 2)[0]);
                    String expression = prefix ? "^(?:" + part[4] + ")" : "(?:" + part[4] + ")$";
                    rules.computeIfAbsent(flag, ignored -> new ArrayList<>()).add(
                            new Rule(prefix, flag, cross.getOrDefault(flag, false), strip, add, Pattern.compile(expression)));
                } else if (part[0].equals("REP") && part.length >= 3 && !part[1].chars().allMatch(Character::isDigit)) {
                    replacements.add(new String[]{part[1].replace('_', ' '), part[2].replace('_', ' ')});
                }
            }
        }
        return new AffixData(rules, replacements);
    }

    private static String zero(String value) { return value.equals("0") ? "" : value; }
    private static int unescapedSlash(String value) {
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) == '/' && (i == 0 || value.charAt(i - 1) != '\\')) return i;
        return -1;
    }
    private static java.io.InputStream requiredResource(String name) {
        var stream = HunspellDictionary.class.getResourceAsStream(name);
        if (stream == null) throw new IllegalStateException("Missing bundled Hunspell resource: " + name);
        return stream;
    }
}
