package org.dromara.certmuse.question.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 题集列表查询的持久化投影。
 */
@Data
public class CollectionListRow {
    private String collectionId;
    private String collectionCode;
    private String parentCollectionName;
    private OffsetDateTime collectionUpdatedTime;
    private String revisionId;
    private Integer revisionNo;
    private String collectionName;
    private String collectionType;
    private String certificationId;
    private String certificationName;
    private String syllabusVersionId;
    private String syllabusVersionName;
    private String status;
    private Boolean currentPublished;
    private Boolean hasReviewOpinion;
    private Long rowVersion;
    private Integer questionCount;
    private BigDecimal totalReportScore;
    private Integer durationMinutes;
    private OffsetDateTime updatedTime;
}
