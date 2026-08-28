package org.dromara.certmuse.learning.domain.vo;

import java.time.OffsetDateTime;

/** One completed exam in the learning-history list. */
public record HistoryExamRowVo(String sessionId, String examType, String title, SubjectVo subject,
                               OffsetDateTime completedAt, HistoryExamResultVo result,
                               Long durationSeconds, Long durationLimitSeconds, String durationStatus) {
    public record SubjectVo(String id, String name) {}
}
