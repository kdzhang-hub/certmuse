package org.dromara.certmuse.assessment.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Published simulation summary safe for learner list display. */
public record SimulationListItemVo(
    String collectionId,
    String revisionId,
    String collectionCode,
    String collectionName,
    String certificationId,
    String certificationName,
    String syllabusVersionId,
    String syllabusVersionName,
    SimulationSubjectVo subject,
    List<String> questionTypes,
    int questionCount,
    BigDecimal totalReportScore,
    int durationMinutes,
    OffsetDateTime publishedTime,
    SimulationAccessVo access
) {
}
