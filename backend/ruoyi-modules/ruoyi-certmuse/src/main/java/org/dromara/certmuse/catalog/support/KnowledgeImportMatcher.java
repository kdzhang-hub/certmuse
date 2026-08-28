package org.dromara.certmuse.catalog.support;

import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.catalog.domain.KnowledgeImportMatch;
import org.dromara.certmuse.catalog.domain.KnowledgeImportPointRow;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Builds explainable, subject-isolated knowledge point match suggestions. */
@Component
@RequiredArgsConstructor
public class KnowledgeImportMatcher {
    /** A candidate must reach 60 points before it may inherit an old node identity. */
    private static final int MINIMUM_IDENTITY_SCORE = 6000;

    private final HungarianMatcher hungarianMatcher;

    public List<KnowledgeImportMatch> match(
        List<KnowledgeImportPointRow> incoming,
        List<KnowledgeImportPointRow> existing
    ) {
        Map<Long, List<KnowledgeImportPointRow>> incomingBySubject = incoming.stream()
            .collect(Collectors.groupingBy(KnowledgeImportPointRow::getExamSubjectId));
        Map<Long, List<KnowledgeImportPointRow>> existingBySubject = existing.stream()
            .collect(Collectors.groupingBy(KnowledgeImportPointRow::getExamSubjectId));
        Set<Long> subjects = new HashSet<>(incomingBySubject.keySet());
        subjects.addAll(existingBySubject.keySet());
        List<KnowledgeImportMatch> result = new ArrayList<>();
        for (Long subject : subjects) {
            result.addAll(matchSubject(
                incomingBySubject.getOrDefault(subject, List.of()),
                existingBySubject.getOrDefault(subject, List.of())
            ));
        }
        return result;
    }

    private List<KnowledgeImportMatch> matchSubject(
        List<KnowledgeImportPointRow> incoming,
        List<KnowledgeImportPointRow> existing
    ) {
        if (incoming.isEmpty()) return List.of();
        Map<String, Set<String>> incomingChildren = children(incoming);
        Map<String, Set<String>> existingChildren = children(existing);
        KnowledgeImportMatch[] matches = new KnowledgeImportMatch[incoming.size()];
        Set<Integer> usedExisting = new HashSet<>();

        assign(
            indexes(incoming, row -> row.getParentSyllabusNumber() == null),
            indexes(existing, row -> row.getParentSyllabusNumber() == null),
            incoming, existing, incomingChildren, existingChildren, matches, usedExisting
        );

        int maxDepth = incoming.stream().mapToInt(KnowledgeImportPointRow::getTreeDepth).max().orElse(0);
        for (int depth = 1; depth <= maxDepth; depth++) {
            Map<Long, List<Integer>> incomingByMatchedParent = new HashMap<>();
            for (int index = 0; index < incoming.size(); index++) {
                KnowledgeImportPointRow row = incoming.get(index);
                if (matches[index] != null || row.getTreeDepth() != depth || row.getParentSyllabusNumber() == null) continue;
                int parentIndex = findByNumber(incoming, row.getParentSyllabusNumber());
                if (parentIndex >= 0 && matches[parentIndex] != null && matches[parentIndex].existing() != null) {
                    incomingByMatchedParent.computeIfAbsent(
                        matches[parentIndex].existing().getKnowledgePointId(), ignored -> new ArrayList<>()
                    ).add(index);
                }
            }
            for (Map.Entry<Long, List<Integer>> entry : incomingByMatchedParent.entrySet()) {
                List<Integer> oldChildren = new ArrayList<>();
                for (int oldIndex = 0; oldIndex < existing.size(); oldIndex++) {
                    if (!usedExisting.contains(oldIndex)
                        && Objects.equals(existing.get(oldIndex).getParentKnowledgePointId(), entry.getKey())) {
                        oldChildren.add(oldIndex);
                    }
                }
                assign(entry.getValue(), oldChildren, incoming, existing, incomingChildren, existingChildren,
                    matches, usedExisting);
            }
        }

        List<Integer> remainingIncoming = new ArrayList<>();
        List<Integer> remainingExisting = new ArrayList<>();
        for (int index = 0; index < incoming.size(); index++) if (matches[index] == null) remainingIncoming.add(index);
        for (int index = 0; index < existing.size(); index++) if (!usedExisting.contains(index)) remainingExisting.add(index);
        assign(remainingIncoming, remainingExisting, incoming, existing, incomingChildren, existingChildren,
            matches, usedExisting);

        List<KnowledgeImportMatch> result = new ArrayList<>(incoming.size());
        for (int index = 0; index < incoming.size(); index++) {
            result.add(matches[index] == null
                ? new KnowledgeImportMatch(incoming.get(index), null, 0, 0, 0, 0, 0, 0, 0, false, false)
                : matches[index]);
        }
        return result;
    }

    private void assign(
        List<Integer> incomingIndexes,
        List<Integer> existingIndexes,
        List<KnowledgeImportPointRow> incoming,
        List<KnowledgeImportPointRow> existing,
        Map<String, Set<String>> incomingChildren,
        Map<String, Set<String>> existingChildren,
        KnowledgeImportMatch[] matches,
        Set<Integer> usedExisting
    ) {
        if (incomingIndexes.isEmpty() || existingIndexes.isEmpty()) return;
        Score[][] scores = new Score[incomingIndexes.size()][existingIndexes.size()];
        int[][] weights = new int[incomingIndexes.size()][existingIndexes.size()];
        for (int row = 0; row < incomingIndexes.size(); row++) {
            for (int column = 0; column < existingIndexes.size(); column++) {
                scores[row][column] = score(incoming.get(incomingIndexes.get(row)), existing.get(existingIndexes.get(column)),
                    incomingChildren, existingChildren);
                weights[row][column] = scores[row][column].total();
            }
        }
        int[] assignment = hungarianMatcher.maximize(weights);
        for (int row = 0; row < incomingIndexes.size(); row++) {
            int selected = assignment[row];
            if (selected < 0 || selected >= existingIndexes.size()
                || weights[row][selected] < MINIMUM_IDENTITY_SCORE) continue;
            int existingIndex = existingIndexes.get(selected);
            if (!usedExisting.add(existingIndex)) continue;
            Score selectedScore = scores[row][selected];
            int second = 0;
            for (int column = 0; column < existingIndexes.size(); column++) {
                if (column != selected) second = Math.max(second, weights[row][column]);
            }
            int gap = Math.max(0, selectedScore.total() - second);
            KnowledgeImportPointRow next = incoming.get(incomingIndexes.get(row));
            KnowledgeImportPointRow old = existing.get(existingIndex);
            matches[incomingIndexes.get(row)] = new KnowledgeImportMatch(
                next, old, selectedScore.title, selectedScore.parent, selectedScore.description,
                selectedScore.children, selectedScore.number, selectedScore.total(), gap,
                !Objects.equals(next.getParentSyllabusNumber(), old.getParentSyllabusNumber()), gap < 1200
            );
        }
    }

    private static List<Integer> indexes(
        List<KnowledgeImportPointRow> rows,
        java.util.function.Predicate<KnowledgeImportPointRow> predicate
    ) {
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) if (predicate.test(rows.get(index))) result.add(index);
        return result;
    }

    private static int findByNumber(List<KnowledgeImportPointRow> rows, String number) {
        for (int index = 0; index < rows.size(); index++) {
            if (Objects.equals(rows.get(index).getSyllabusNumber(), number)) return index;
        }
        return -1;
    }

    private static Score score(
        KnowledgeImportPointRow incoming,
        KnowledgeImportPointRow existing,
        Map<String, Set<String>> incomingChildren,
        Map<String, Set<String>> existingChildren
    ) {
        int title = scaledSimilarity(incoming.getSyllabusTitle(), existing.getSyllabusTitle(), 4500);
        int parent = scaledSimilarity(incoming.getParentSyllabusTitle(), existing.getParentSyllabusTitle(), 2500);
        int description = scaledSimilarity(incoming.getDescription(), existing.getDescription(), 1500);
        int children = scaledJaccard(
            incomingChildren.getOrDefault(incoming.getSyllabusNumber(), Set.of()),
            existingChildren.getOrDefault(existing.getSyllabusNumber(), Set.of()), 1000
        );
        int number = scaledSimilarity(incoming.getSyllabusNumber(), existing.getSyllabusNumber(), 500);
        return new Score(title, parent, description, children, number);
    }

    private static Map<String, Set<String>> children(List<KnowledgeImportPointRow> rows) {
        Map<String, Set<String>> result = new HashMap<>();
        for (KnowledgeImportPointRow row : rows) {
            if (row.getParentSyllabusNumber() != null) {
                result.computeIfAbsent(row.getParentSyllabusNumber(), ignored -> new HashSet<>())
                    .add(normalize(row.getSyllabusTitle()));
            }
        }
        return result;
    }

    private static int scaledSimilarity(String left, String right, int weight) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isEmpty() && b.isEmpty()) return weight;
        if (a.isEmpty() || b.isEmpty()) return 0;
        if (a.equals(b)) return weight;
        int maxLength = Math.max(a.length(), b.length());
        int editBasis = maxLength == 0 ? 10000 : (maxLength - levenshtein(a, b)) * 10000 / maxLength;
        int jaccardBasis = jaccard(characters(a), characters(b));
        return Math.max(editBasis, jaccardBasis) * weight / 10000;
    }

    private static int scaledJaccard(Set<String> left, Set<String> right, int weight) {
        if (left.isEmpty() && right.isEmpty()) return weight;
        return jaccard(left, right) * weight / 10000;
    }

    private static int jaccard(Set<String> left, Set<String> right) {
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        if (union.isEmpty()) return 10000;
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        return intersection.size() * 10000 / union.size();
    }

    private static Set<String> characters(String value) {
        return value.codePoints().mapToObj(Character::toString).collect(Collectors.toSet());
    }

    private static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            previous = current;
        }
        return previous[right.length()];
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim().toLowerCase(Locale.ROOT)
            .replaceAll("[，。；：、（）【】‘’“”]", " ")
            .replaceAll("\\s+", " ");
    }

    private record Score(int title, int parent, int description, int children, int number) {
        private int total() { return title + parent + description + children + number; }
    }
}
