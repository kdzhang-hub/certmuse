package org.dromara.certmuse.learning.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.learning.domain.LearningTaskCandidateRow;
import org.dromara.certmuse.learning.domain.LearningTaskContentRow;
import org.dromara.certmuse.learning.domain.LearningTaskGoalRow;
import org.dromara.certmuse.learning.domain.LearningTaskIdempotencyRow;
import org.dromara.certmuse.learning.domain.LearningTaskQuestionRow;
import org.dromara.certmuse.learning.domain.LearningTaskRow;
import org.dromara.certmuse.learning.domain.LearningTaskSessionRow;
import org.dromara.certmuse.learning.domain.vo.LearningTaskListItemVo;

/** PostgreSQL persistence for the U10 task pool, locking and frozen snapshots. */
public interface LearningTaskMapper {
    LearningTaskGoalRow selectActiveGoal(long userId);
    LearningTaskGoalRow lockActiveGoal(long userId);
    LearningTaskGoalRow lockGoalById(long goalId);
    List<Long> selectActiveGoalIds(@Param("offset") int offset, @Param("limit") int limit);
    int countIncompleteTasks(@Param("userId") long userId, @Param("goalId") long goalId);
    long countTaskPage(@Param("userId") long userId, @Param("goalId") long goalId,
                       @Param("keyword") String keyword);
    List<LearningTaskListItemVo> selectTaskPage(@Param("userId") long userId, @Param("goalId") long goalId,
                                                @Param("keyword") String keyword, @Param("offset") int offset,
                                                @Param("limit") int limit);
    LearningTaskSessionRow lockActiveDailySession(@Param("userId") long userId, @Param("goalId") long goalId);
    LearningTaskRow lockOwnedTask(@Param("userId") long userId, @Param("goalId") long goalId,
                                  @Param("taskId") long taskId);
    LearningTaskContentRow selectOwnedLearningContent(@Param("userId") long userId,
                                                       @Param("goalId") long goalId,
                                                       @Param("taskId") long taskId);
    LearningTaskSessionRow selectTaskSession(@Param("userId") long userId, @Param("goalId") long goalId,
                                              @Param("taskId") long taskId);
    int insertSession(@Param("id") long id, @Param("userId") long userId,
                      @Param("goal") LearningTaskGoalRow goal, @Param("task") LearningTaskRow task,
                      @Param("requestId") String requestId);
    List<LearningTaskQuestionRow> selectTaskQuestions(long taskItemId);
    int insertSessionQuestion(@Param("id") long id, @Param("sessionId") long sessionId,
                              @Param("order") int order, @Param("question") LearningTaskQuestionRow question);
    int insertTaskAttempt(@Param("id") long id, @Param("taskItemId") long taskItemId,
                          @Param("sessionId") long sessionId);
    int startPracticeSession(@Param("sessionId") long sessionId);
    int completeLearningAttempt(@Param("taskItemId") long taskItemId, @Param("sessionId") long sessionId);
    int completePracticeItem(@Param("userId") long userId, @Param("sessionId") long sessionId);
    LearningTaskIdempotencyRow selectIdempotency(@Param("actionCode") String actionCode,
                                                  @Param("requestId") String requestId);
    int insertIdempotency(@Param("id") long id, @Param("actionCode") String actionCode,
                          @Param("requestId") String requestId, @Param("payloadHash") String payloadHash);
    int succeedIdempotency(@Param("id") long id, @Param("resourceType") String resourceType,
                           @Param("resourceId") Long resourceId, @Param("responseBody") String responseBody);
    int failIdempotency(@Param("id") long id, @Param("responseStatus") int responseStatus,
                        @Param("responseBody") String responseBody);
    Long selectUniquePublishedRuleVersion();
    int countPublishedRuleVersions();
    int insertBatch(@Param("id") long id, @Param("userId") long userId, @Param("goalId") long goalId,
                    @Param("triggerType") String triggerType, @Param("beforeCount") int beforeCount,
                    @Param("requestedCount") int requestedCount, @Param("ruleVersionId") Long ruleVersionId,
                    @Param("requestId") String requestId, @Param("generationKey") String generationKey,
                    @Param("inputSnapshot") String inputSnapshot);
    Long selectBatchIdByGenerationKey(String generationKey);
    int restartFailedBatch(@Param("generationKey") String generationKey,
                           @Param("beforeCount") int beforeCount,
                           @Param("requestedCount") int requestedCount,
                           @Param("ruleVersionId") Long ruleVersionId,
                           @Param("inputSnapshot") String inputSnapshot);
    List<LearningTaskCandidateRow> selectCandidates(@Param("userId") long userId, @Param("goalId") long goalId,
                                                     @Param("syllabusVersionId") long syllabusVersionId,
                                                     @Param("learningContentSchemaVersion") String learningContentSchemaVersion,
                                                     @Param("limit") int limit);
    List<LearningTaskQuestionRow> selectCandidateQuestions(@Param("userId") long userId,
                                                            @Param("goalId") long goalId,
                                                            @Param("syllabusVersionId") long syllabusVersionId,
                                                            @Param("knowledgePointId") long knowledgePointId,
                                                            @Param("limit") int limit);
    int insertTask(@Param("id") long id, @Param("userId") long userId, @Param("goalId") long goalId,
                   @Param("candidate") LearningTaskCandidateRow candidate, @Param("batchId") long batchId,
                   @Param("ruleVersionId") long ruleVersionId, @Param("ordinal") int ordinal,
                   @Param("estimatedMinutes") int estimatedMinutes, @Param("displaySnapshot") String displaySnapshot,
                   @Param("profileSnapshot") String profileSnapshot);
    int insertTaskItem(@Param("id") long id, @Param("taskId") long taskId, @Param("order") int order,
                       @Param("type") String type, @Param("candidate") LearningTaskCandidateRow candidate,
                       @Param("estimatedMinutes") int estimatedMinutes, @Param("targetData") String targetData);
    int insertTaskQuestion(@Param("id") long id, @Param("taskItemId") long taskItemId,
                           @Param("order") int order, @Param("question") LearningTaskQuestionRow question);
    int completeBatch(@Param("id") long id, @Param("createdCount") int createdCount,
                      @Param("resultSummary") String resultSummary);
    int failBatch(@Param("id") long id, @Param("failureReason") String failureReason,
                  @Param("resultSummary") String resultSummary);
}
