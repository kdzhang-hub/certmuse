package org.dromara.certmuse.learning.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** All valid submissions and correction events for one logical question. */
public record HistoryQuestionDetailVo(String questionId, String stemPreview, long attemptCount,
                                      List<AttemptVo> history, CorrectionVo correction) {
    public record AttemptVo(String attemptId, String sessionId, String sessionSource,
                            OffsetDateTime answeredAt, String stemPreview, String questionType,
                            String difficulty, List<LookupVo> knowledgePoints, String attemptResult,
                            String answerSummary, String score, String maxScore,
                            BigDecimal scoreRate, boolean skipped) {}
    public record LookupVo(String id, String name) {}
    public record CorrectionVo(String status, long wrongCount, long skipCount, List<EventVo> events) {}
    public record EventVo(OffsetDateTime occurredAt, String eventType, String sessionSource) {}
}
