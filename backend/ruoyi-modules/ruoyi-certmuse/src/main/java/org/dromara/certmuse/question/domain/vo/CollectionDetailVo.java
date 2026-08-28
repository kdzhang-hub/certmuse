package org.dromara.certmuse.question.domain.vo;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;

/**
 * 题集详情及其修订历史。
 */
public record CollectionDetailVo(
    String collectionId, String collectionCode,
    CollectionRevisionDetailVo displayRevision,
    RevisionSummaryVo currentPublishedRevision,
    List<RevisionSummaryVo> revisions,
    List<String> allowedActions
) {
    /**
     * 修订历史摘要。
     */
    public record RevisionSummaryVo(
        String revisionId, int revisionNo, String status,
        boolean hasReviewOpinion,
        /** 是否由 current 指针指定为当前生效的发布修订。 */ boolean currentPublished,
        String rowVersion, int questionCount, BigDecimal totalReportScore,
        OffsetDateTime updatedTime
    ) { }
}
