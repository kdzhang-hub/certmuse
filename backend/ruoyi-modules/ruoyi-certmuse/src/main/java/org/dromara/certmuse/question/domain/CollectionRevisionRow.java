package org.dromara.certmuse.question.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 题集修订查询的持久化投影。
 */
@Data
public class CollectionRevisionRow {
    private Long id;
    private Long collectionId;
    private String collectionCode;
    private Integer revisionNo;
    private String status;
    private Long rowVersion;
    private String collectionName;
    private String collectionType;
    private Long certificationId;
    private String certificationName;
    private Long syllabusVersionId;
    private String syllabusVersionName;
    private Integer questionCount;
    private BigDecimal totalReportScore;
    private Integer durationMinutes;
    private Boolean pauseAllowed;
    private String reviewOpinion;
    private OffsetDateTime updatedTime;
}
