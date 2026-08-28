package org.dromara.certmuse.learning.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;

/** One incomplete task displayed in the learner task pool. */
@Data
public class LearningTaskListItemVo {
    private String id;
    private String title;
    private List<String> phaseSummary;
    private String knowledgePoint;
    private int questionCount;
    private int completedQuestionCount;
    private int estimatedMinutes;
    private OffsetDateTime updatedAt;
    private String recommendation;
    private String action;
}
