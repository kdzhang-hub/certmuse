package org.dromara.certmuse.assessment.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.assessment.domain.AiGradingTaskRow;
import org.dromara.certmuse.assessment.domain.QuestionAiRubricRow;

/** PostgreSQL persistence for durable subjective-grading tasks. */
public interface AiGradingMapper {
    QuestionAiRubricRow selectRubric(long questionRevisionId);
    int insertRubric(@Param("id") long id, @Param("revisionId") long revisionId, @Param("contextHash") String contextHash,
                     @Param("rubric") String rubric, @Param("model") String model, @Param("promptVersion") String promptVersion);
    int insertTask(@Param("id") long id, @Param("taskKey") String taskKey, @Param("taskType") String taskType,
                   @Param("revisionId") Long revisionId, @Param("sessionQuestionId") Long sessionQuestionId,
                   @Param("attemptId") Long attemptId, @Param("payload") String payload);
    AiGradingTaskRow claimTask();
    int reclaimExpiredTasks(@Param("leaseSeconds") long leaseSeconds);
    AiGradingTaskRow selectTask(@Param("taskKey") String taskKey);
    void succeedTask(@Param("id") long id, @Param("result") String result);
    int retryTask(@Param("id") long id, @Param("errorCode") String errorCode);
    void failTask(@Param("id") long id, @Param("errorCode") String errorCode);
}
