package org.dromara.certmuse.learning.service;

import java.time.LocalDate;
import java.util.List;
import org.dromara.certmuse.learning.domain.bo.LearningTaskQueryBo;
import org.dromara.certmuse.learning.domain.vo.LearningTaskLaunchVo;
import org.dromara.certmuse.learning.domain.vo.DailyTaskLearningContentVo;
import org.dromara.certmuse.learning.domain.vo.LearningTaskPageVo;
import org.dromara.certmuse.learning.domain.vo.LearningTaskSupplementVo;
import org.dromara.certmuse.learning.domain.vo.StartDailyTaskPracticeVo;

/** U10 task-pool use cases shared by HTTP and the midnight scheduler. */
public interface LearningTaskService {
    LearningTaskPageVo page(long userId, LearningTaskQueryBo query);
    LearningTaskLaunchVo launch(long userId, String taskId, String requestId);
    DailyTaskLearningContentVo learningContent(long userId, String taskId);
    StartDailyTaskPracticeVo startPractice(long userId, String taskId, String requestId);
    void completePracticeItem(long userId, long sessionId);
    LearningTaskSupplementVo supplement(long userId, String requestId);
    List<Long> activeGoalIds(int offset, int limit);
    void supplementScheduled(long goalId, LocalDate businessDate);
}
