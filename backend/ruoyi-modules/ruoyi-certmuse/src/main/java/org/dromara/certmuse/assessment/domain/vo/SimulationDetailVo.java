package org.dromara.certmuse.assessment.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Published simulation instructions without question or grading content. */
public record SimulationDetailVo(
    String collectionId,
    String revisionId,
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
    String examMode,
    List<String> rules,
    SimulationAccessVo access
) {
}
