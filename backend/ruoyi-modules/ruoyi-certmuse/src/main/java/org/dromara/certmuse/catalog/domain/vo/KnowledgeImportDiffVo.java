package org.dromara.certmuse.catalog.domain.vo;

import java.math.BigDecimal;
import java.util.List;

/** One safe, display-ready knowledge import difference. */
public record KnowledgeImportDiffVo(
    String id,
    String examSubjectId,
    String subjectName,
    PointVo oldPoint,
    PointVo newPoint,
    PointVo suggestedPoint,
    PointVo confirmedPoint,
    String action,
    String resolutionStatus,
    String resolutionDecision,
    String parentDiffId,
    BigDecimal matchScore,
    EvidenceVo evidence,
    List<String> changedFields
) {
    public record PointVo(String id, String syllabusNumber, String syllabusTitle) { }
    public record EvidenceVo(
        BigDecimal titleScore,
        BigDecimal parentScore,
        BigDecimal descriptionScore,
        BigDecimal childrenScore,
        BigDecimal numberScore,
        BigDecimal totalScore,
        BigDecimal candidateGap,
        boolean possibleMove,
        boolean ambiguous
    ) { }
}
