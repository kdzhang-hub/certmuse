package org.dromara.certmuse.assessment.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeGoalRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeAnsweringSessionRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeItemRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeNavigationRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeParentAggregateRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeIdempotencyRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeNodeRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeQuestionRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeSessionRow;

/** PostgreSQL persistence for recursive U08 reads and atomic session creation. */
public interface KnowledgePracticeMapper {
    KnowledgePracticeGoalRow selectCurrentGoal(long userId);
    int countDirectoryViolations(long syllabusVersionId);
    List<KnowledgePracticeNodeRow> selectVisibleNodes(@Param("userId") long userId,
                                                       @Param("goalId") long goalId,
                                                       @Param("syllabusVersionId") long syllabusVersionId);
    KnowledgePracticeNodeRow selectPracticeNode(@Param("userId") long userId,
                                                 @Param("goalId") long goalId,
                                                 @Param("syllabusVersionId") long syllabusVersionId,
                                                 @Param("knowledgePointId") long knowledgePointId);
    List<KnowledgePracticeQuestionRow> selectQualifiedQuestions(@Param("syllabusVersionId") long syllabusVersionId,
                                                                 @Param("knowledgePointId") long knowledgePointId);
    Long selectPublishedRuleVersion();
    KnowledgePracticeIdempotencyRow selectIdempotency(String requestId);
    int insertIdempotency(@Param("id") long id, @Param("requestId") String requestId,
                          @Param("payloadHash") String payloadHash);
    int insertSession(@Param("id") long id, @Param("userId") long userId,
                      @Param("goal") KnowledgePracticeGoalRow goal, @Param("subjectId") long subjectId,
                      @Param("ruleVersionId") long ruleVersionId, @Param("requestId") String requestId);
    KnowledgePracticeSessionRow selectActiveSession(@Param("userId") long userId, @Param("goalId") long goalId);
    KnowledgePracticeSessionRow selectSessionByRequestId(String requestId);
    KnowledgePracticeSessionRow selectOwnedSession(@Param("sessionId") long sessionId, @Param("userId") long userId);
    void insertSessionQuestion(@Param("id") long id, @Param("sessionId") long sessionId,
                               @Param("question") KnowledgePracticeQuestionRow question,
                               @Param("questionOrder") int questionOrder,
                               @Param("presentation") String presentation,
                               @Param("grading") String grading,
                               @Param("knowledge") String knowledge);
    void completeIdempotency(@Param("id") long id, @Param("sessionId") long sessionId,
                             @Param("responseBody") String responseBody);
    KnowledgePracticeAnsweringSessionRow selectAnsweringSession(@Param("sessionId") long sessionId,
                                                                 @Param("userId") long userId);
    int startAnsweringSession(@Param("sessionId") long sessionId, @Param("userId") long userId);
    List<KnowledgePracticeNavigationRow> selectNavigation(long sessionId);
    KnowledgePracticeItemRow selectAnsweringItem(@Param("sessionId") long sessionId,
                                                  @Param("userId") long userId,
                                                  @Param("questionOrder") int questionOrder);
    KnowledgePracticeAnsweringSessionRow lockAnsweringSession(@Param("sessionId") long sessionId,
                                                               @Param("userId") long userId);
    KnowledgePracticeIdempotencyRow selectActionIdempotency(@Param("actionCode") String actionCode,
                                                             @Param("requestId") String requestId);
    int insertActionIdempotency(@Param("id") long id, @Param("actionCode") String actionCode,
                                @Param("requestId") String requestId, @Param("payloadHash") String payloadHash);
    void succeedActionIdempotency(@Param("id") long id, @Param("resourceType") String resourceType,
                                  @Param("resourceId") long resourceId, @Param("responseBody") String responseBody);
    int insertAttempt(@Param("id") long id, @Param("sessionQuestionId") long sessionQuestionId,
                      @Param("userId") long userId, @Param("requestId") String requestId);
    void insertAttemptAnswer(@Param("id") long id, @Param("attemptId") long attemptId,
                             @Param("answerData") String answerData, @Param("correct") boolean correct);
    int insertErrorRecord(@Param("id") long id, @Param("userId") long userId, @Param("goalId") long goalId,
                          @Param("attemptId") long attemptId, @Param("knowledgePointId") long knowledgePointId,
                          @Param("groupKey") String groupKey);
    Long insertSingleSettlement(@Param("id") long id, @Param("item") KnowledgePracticeItemRow item,
                                @Param("attemptId") long attemptId, @Param("knowledgePointId") long knowledgePointId,
                                @Param("correct") boolean correct, @Param("requestId") String requestId);
    void insertPracticeEvidence(@Param("id") long id, @Param("settlementId") long settlementId,
                                @Param("item") KnowledgePracticeItemRow item, @Param("attemptId") long attemptId,
                                @Param("knowledgePointId") long knowledgePointId, @Param("correct") boolean correct);
    void upsertPracticeKnowledgeProfile(@Param("id") long id, @Param("item") KnowledgePracticeItemRow item,
                                        @Param("knowledgePointId") long knowledgePointId, @Param("correct") boolean correct);
    void insertPracticeKnowledgeChange(@Param("id") long id, @Param("item") KnowledgePracticeItemRow item,
                                       @Param("settlementId") long settlementId,
                                       @Param("knowledgePointId") long knowledgePointId,
                                       @Param("requestId") String requestId);
    List<KnowledgePracticeParentAggregateRow> selectParentAggregates(@Param("userId") long userId,
                                                                      @Param("goalId") long goalId);
    void upsertParentProfile(@Param("id") long id, @Param("userId") long userId, @Param("goalId") long goalId,
                             @Param("ruleVersionId") long ruleVersionId,
                             @Param("aggregate") KnowledgePracticeParentAggregateRow aggregate);
    void aggregateSubjectProfiles(@Param("userId") long userId, @Param("goalId") long goalId,
                                  @Param("ruleVersionId") long ruleVersionId);
    void aggregateOverallProfile(@Param("userId") long userId, @Param("goalId") long goalId,
                                 @Param("ruleVersionId") long ruleVersionId);
    int countSubmittedItems(long sessionId);
    int submitAnsweringSession(@Param("sessionId") long sessionId, @Param("userId") long userId);
    int settleAnsweringSession(@Param("sessionId") long sessionId, @Param("userId") long userId);
    int completeAnsweringSession(@Param("sessionId") long sessionId, @Param("userId") long userId);
}
