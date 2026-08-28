package org.dromara.certmuse.learning.domain.vo;

import java.util.List;

/** Frozen textbook content returned before daily-task practice starts. */
public record DailyTaskLearningContentVo(
    String taskId,
    String phase,
    String title,
    String knowledgePoint,
    int estimatedMinutes,
    List<SectionVo> sections,
    String nextAction
) {
    public record SectionVo(int order, String title, String content, List<ImageVo> images) {
    }

    public record ImageVo(String url, String alt, int sortOrder) {
    }
}
