package org.dromara.certmuse.assessment.domain.vo;

import java.math.BigDecimal;
import java.util.List;

/** Visible knowledge tree node returned by U08. */
public record KnowledgePracticeNodeVo(
    String id, String parentId, String examSubjectId, String syllabusNumber, String syllabusTitle,
    int treeDepth, int sortOrder, int importance, long questionCount,
    KnowledgePracticeMasteryVo mastery, List<KnowledgePracticeNodeVo> children
) {
}
