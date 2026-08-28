package org.dromara.certmuse.catalog.domain;

/** One algorithmic old-to-new knowledge point suggestion with explainable scores. */
public record KnowledgeImportMatch(
    KnowledgeImportPointRow incoming,
    KnowledgeImportPointRow existing,
    int titleScore,
    int parentScore,
    int descriptionScore,
    int childrenScore,
    int numberScore,
    int totalScore,
    int candidateGap,
    boolean possibleMove,
    boolean ambiguous
) {
}
