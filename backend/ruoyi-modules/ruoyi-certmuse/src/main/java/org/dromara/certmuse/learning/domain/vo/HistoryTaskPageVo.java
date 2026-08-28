package org.dromara.certmuse.learning.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;

/** Aggregated completed daily-task history page. */
public record HistoryTaskPageVo(List<RowVo> rows, long total) {
    public record RowVo(String taskId, String title, LookupVo knowledgePoint, LookupVo subject,
                        int questionCount, int correctCount, int incorrectCount,
                        int skippedCount, OffsetDateTime completedAt) {}
    public record LookupVo(String id, String name) {}
}
