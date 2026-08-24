package com.allgos.dms.file.service;

import com.allgos.dms.department.entity.Department;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Matches a department name read off a document — "Public Works [Estt-I(1)] Department", say — to
 * one of the 43 seeded {@link Department} rows, whose own names read "Department of Public Works".
 *
 * <p>Neither side is the other's exact text, so this compares the significant words each name is
 * built from rather than the strings themselves: a document's own bracketed sub-office code
 * ("[Estt-I(1)]") and the word "Department" itself carry no information about which of the 43 this
 * is, so both are stripped before comparing. What is left has to overlap by at least half — measured
 * as <a href="https://en.wikipedia.org/wiki/Jaccard_index">Jaccard similarity</a> — before a guess is
 * offered at all; a low-confidence guess in a records system is worse than no guess, since a document
 * silently filed under the wrong department is not obviously wrong the way an empty field is.
 */
@Component
public class DepartmentMatcher {

    private static final double MIN_SIMILARITY = 0.5;

    private static final Pattern BRACKETED = Pattern.compile("[\\(\\[][^)\\]]*[\\)\\]]");
    private static final Pattern NON_LETTER = Pattern.compile("[^a-z\\s]");
    private static final Set<String> STOPWORDS =
            Set.of("department", "of", "and", "the", "in", "for", "to", "govt", "government");

    public Optional<Department> match(String hint, List<Department> candidates) {
        if (hint == null || hint.isBlank()) {
            return Optional.empty();
        }

        Set<String> hintWords = significantWords(hint);
        if (hintWords.isEmpty()) {
            return Optional.empty();
        }

        Department best = null;
        double bestSimilarity = 0;

        for (Department candidate : candidates) {
            Set<String> candidateWords = significantWords(candidate.getName());
            if (candidateWords.isEmpty()) {
                continue;
            }

            double similarity = jaccard(hintWords, candidateWords);
            if (similarity > bestSimilarity) {
                best = candidate;
                bestSimilarity = similarity;
            }
        }

        return bestSimilarity >= MIN_SIMILARITY ? Optional.ofNullable(best) : Optional.empty();
    }

    private static Set<String> significantWords(String name) {
        String stripped = BRACKETED.matcher(name.toLowerCase(Locale.ROOT)).replaceAll(" ");
        stripped = NON_LETTER.matcher(stripped).replaceAll(" ");

        Set<String> words = new HashSet<>();
        for (String word : stripped.split("\\s+")) {
            if (word.length() > 1 && !STOPWORDS.contains(word)) {
                words.add(word);
            }
        }
        return words;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        if (intersection.isEmpty()) {
            return 0;
        }

        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }
}
