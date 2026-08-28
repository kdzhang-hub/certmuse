package org.dromara.certmuse.question.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 管理端题集列表项。
 */
public record CollectionListVo(
    String collectionId, String collectionCode, String revisionId, int revisionNo,
    String collectionName, String collectionType,
    String certificationId, String certificationName,
    String syllabusVersionId, String syllabusVersionName,
    String status, boolean currentPublished, boolean hasReviewOpinion, String rowVersion,
    int questionCount, BigDecimal totalReportScore, Integer durationMinutes, OffsetDateTime updatedTime
) { }
