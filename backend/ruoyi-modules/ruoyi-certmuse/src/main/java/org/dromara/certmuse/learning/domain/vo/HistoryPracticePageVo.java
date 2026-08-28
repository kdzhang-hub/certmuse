package org.dromara.certmuse.learning.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;

/** Page of completed practice sessions. */
public record HistoryPracticePageVo(List<RowVo> rows, long total) {
    public record RowVo(String sessionId, String practiceType, String title, LookupVo subject,
                        List<LookupVo> knowledgePoints, int questionCount, int answeredCount,
                        int correctCount, int incorrectCount, int unansweredCount,
                        OffsetDateTime completedAt) { }
    public record LookupVo(String id, String name) { }
}
