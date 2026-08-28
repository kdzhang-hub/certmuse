package org.dromara.certmuse.learning.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Aggregated question-history page. */
public record HistoryQuestionPageVo(List<RowVo> rows, long total) {
    public record RowVo(String questionId, String stemPreview, String questionType, String difficulty,
                        List<LookupVo> knowledgePoints, long attemptCount, OffsetDateTime lastAnsweredAt,
                        AttemptVo lastAttempt, String correctionStatus) {}
    public record LookupVo(String id, String name) {}
    public record AttemptVo(String sessionSource, String attemptResult, BigDecimal scoreRate) {}
}
