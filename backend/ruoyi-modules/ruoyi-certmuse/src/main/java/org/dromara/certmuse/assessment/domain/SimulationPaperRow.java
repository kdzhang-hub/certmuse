package org.dromara.certmuse.assessment.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/** Current published simulation summary and aggregate projection. */
@Data
public class SimulationPaperRow {
    private Long collectionId;
    private Long revisionId;
    private String collectionCode;
    private String collectionName;
    private Long certificationId;
    private String certificationName;
    private Long syllabusVersionId;
    private String syllabusVersionName;
    private Integer questionCount;
    private BigDecimal totalReportScore;
    private Integer durationMinutes;
    private OffsetDateTime publishedTime;
    private Integer subjectCount;
    private Long subjectId;
    private String subjectName;
    private Boolean hasChoice;
    private Boolean hasCase;
    private Boolean hasEssay;
    private Integer actualQuestionCount;
    private BigDecimal actualTotalReportScore;
    private Long activeSessionId;
    private String activeSessionStatus;
}
