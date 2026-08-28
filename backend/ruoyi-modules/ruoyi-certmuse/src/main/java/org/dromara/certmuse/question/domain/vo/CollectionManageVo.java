package org.dromara.certmuse.question.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 题集管理页的父行及完整修订摘要。
 */
public record CollectionManageVo(
    String collectionId, String collectionCode, String collectionName, String collectionType,
    String certificationId, String certificationName, String syllabusVersionId, String syllabusVersionName,
    String currentPublishedRevisionId, Integer currentPublishedRevisionNo, OffsetDateTime updatedTime,
    List<RevisionSummaryVo> revisions
) {
    public record RevisionSummaryVo(
        String revisionId, int revisionNo, String status, boolean hasReviewOpinion,
        boolean currentPublished, String rowVersion, int questionCount,
        BigDecimal totalReportScore, OffsetDateTime updatedTime
    ) { }
}
