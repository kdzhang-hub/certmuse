package org.dromara.certmuse.assessment.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.assessment.domain.FormalExamItemRow;
import org.dromara.certmuse.assessment.domain.FormalExamJobRow;
import org.dromara.certmuse.assessment.domain.FormalExamSessionRow;
import org.dromara.certmuse.assessment.domain.FormalExamTimerLeaseRow;
import org.dromara.certmuse.assessment.domain.PastPaperGoalRow;
import org.dromara.certmuse.assessment.domain.PastPaperIdempotencyRow;
import org.dromara.certmuse.assessment.domain.PastPaperPaperRow;
import org.dromara.certmuse.assessment.domain.PastPaperQuestionRow;

/** PostgreSQL persistence shared only by past-paper and simulation formal examinations. */
public interface FormalExamMapper {
    PastPaperGoalRow selectActiveGoal(long userId);
    PastPaperPaperRow selectCurrentPaper(@Param("collectionId") long collectionId,
                                         @Param("collectionType") String collectionType);
    List<PastPaperQuestionRow> selectCurrentQuestions(@Param("collectionId") long collectionId,
                                                      @Param("collectionType") String collectionType);
    Long selectPublishedRuleVersion();
    FormalExamSessionRow selectActiveSession(@Param("userId") long userId, @Param("goalId") long goalId,
                                              @Param("sessionType") String sessionType);
    int nextFormalAttemptNo(@Param("userId") long userId, @Param("goalId") long goalId,
                            @Param("revisionId") long revisionId, @Param("sessionType") String sessionType);
    int insertSession(@Param("id") long id, @Param("userId") long userId, @Param("goal") PastPaperGoalRow goal,
                      @Param("revisionId") long revisionId, @Param("sessionType") String sessionType,
                      @Param("ruleVersionId") long ruleVersionId, @Param("requestId") String requestId,
                      @Param("durationSeconds") int durationSeconds, @Param("formalAttemptNo") int formalAttemptNo);
    void insertSessionQuestion(@Param("id") long id, @Param("sessionId") long sessionId,
                               @Param("question") PastPaperQuestionRow question,
                               @Param("presentation") String presentation, @Param("grading") String grading,
                               @Param("knowledge") String knowledge);
    void insertDraftAttempt(@Param("attemptId") long attemptId, @Param("answerId") long answerId,
                            @Param("sessionQuestionId") long sessionQuestionId, @Param("userId") long userId,
                            @Param("requestId") String requestId, @Param("answerData") String answerData);
    FormalExamSessionRow selectSession(@Param("userId") long userId, @Param("sessionId") long sessionId,
                                       @Param("sessionType") String sessionType);
    FormalExamSessionRow lockSession(@Param("userId") long userId, @Param("sessionId") long sessionId,
                                     @Param("sessionType") String sessionType);
    FormalExamSessionRow lockSessionForWorker(long sessionId);
    List<FormalExamItemRow> selectItems(@Param("userId") long userId, @Param("sessionId") long sessionId);
    FormalExamItemRow selectItem(@Param("userId") long userId, @Param("sessionId") long sessionId,
                                 @Param("questionOrder") int questionOrder);
    int updateDraft(@Param("answerId") long answerId, @Param("answerData") String answerData);
    int updateSessionPosition(@Param("sessionId") long sessionId, @Param("expectedVersion") long expectedVersion,
                              @Param("questionOrder") int questionOrder);
    FormalExamTimerLeaseRow lockTimerLease(long sessionId);
    FormalExamTimerLeaseRow selectTimerLease(long sessionId);
    void deleteTimerLease(long sessionId);
    void insertTimerLease(@Param("sessionId") long sessionId, @Param("questionOrder") int questionOrder,
                          @Param("attemptId") long attemptId, @Param("leaseId") String leaseId);
    int accumulateTimer(@Param("attemptId") long attemptId, @Param("seconds") int seconds);
    void activateTimer(long attemptId);
    void markTimerInvalid(@Param("attemptId") long attemptId, @Param("reason") String reason);
    int markSubmitted(@Param("sessionId") long sessionId, @Param("expectedVersion") Long expectedVersion);
    void submitAttempts(long sessionId);
    int markSettling(long sessionId);
    int markCompleted(long sessionId);
    int insertJob(@Param("id") long id, @Param("businessKey") String businessKey,
                  @Param("payload") String payload);
    String selectJobStatus(@Param("businessKey") String businessKey);
    FormalExamJobRow claimJob();
    void markJobSucceeded(long id);
    void requeueJob(long id);
    void markJobFailed(@Param("id") long id, @Param("message") String message);
    void retryJob(@Param("businessKey") String businessKey);
    List<FormalExamSessionRow> lockExpiredRunningSessions(@Param("limit") int limit);
    PastPaperIdempotencyRow selectIdempotency(@Param("action") String action, @Param("requestId") String requestId);
    int insertIdempotency(@Param("id") long id, @Param("action") String action, @Param("requestId") String requestId,
                          @Param("payloadHash") String payloadHash);
    void completeIdempotency(@Param("id") long id, @Param("resourceType") String resourceType,
                             @Param("resourceId") long resourceId, @Param("responseBody") String responseBody);
    void gradeAnswer(@Param("answerId") long answerId, @Param("score") String score,
                     @Param("maxScore") String maxScore, @Param("scoreRate") String scoreRate,
                     @Param("gradingSource") String gradingSource, @Param("gradingResult") String gradingResult);
    void failAnswer(@Param("answerId") long answerId, @Param("maxScore") String maxScore,
                    @Param("gradingResult") String gradingResult);
    void gradeAttempt(@Param("attemptId") long attemptId, @Param("blank") boolean blank,
                      @Param("profileApplied") boolean profileApplied);
    int countRecentEvidence(@Param("userId") long userId, @Param("goalId") long goalId,
                            @Param("evidenceGroupKey") String evidenceGroupKey);
    String selectLatestEvidenceTime(@Param("userId") long userId, @Param("goalId") long goalId,
                                    @Param("evidenceGroupKey") String evidenceGroupKey);
    void insertErrorRecord(@Param("id") long id, @Param("userId") long userId, @Param("goalId") long goalId,
                           @Param("attemptId") long attemptId, @Param("knowledgePointId") long knowledgePointId,
                           @Param("groupKey") String groupKey);
    Long insertSettlement(@Param("id") long id, @Param("userId") long userId, @Param("goalId") long goalId,
                          @Param("sessionId") long sessionId, @Param("attemptId") long attemptId,
                          @Param("subjectId") long subjectId, @Param("knowledgePointId") long knowledgePointId,
                          @Param("coefficient") String coefficient, @Param("ruleVersionId") long ruleVersionId,
                          @Param("requestId") String requestId, @Param("scoreRate") String scoreRate,
                          @Param("direction") String direction);
    void insertEvidence(@Param("id") long id, @Param("settlementId") long settlementId,
                        @Param("userId") long userId, @Param("goalId") long goalId,
                        @Param("sessionId") long sessionId, @Param("attemptId") long attemptId,
                        @Param("subjectId") long subjectId, @Param("knowledgePointId") long knowledgePointId,
                        @Param("groupKey") String groupKey, @Param("behaviorType") String behaviorType,
                        @Param("difficulty") String difficulty, @Param("scoreRate") String scoreRate,
                        @Param("direction") String direction, @Param("coefficient") String coefficient,
                        @Param("distinct") boolean distinct, @Param("ruleVersionId") long ruleVersionId,
                        @Param("elapsed") Integer elapsed, @Param("timerStatus") String timerStatus);
    void upsertKnowledgeProfile(@Param("id") long id, @Param("userId") long userId, @Param("goalId") long goalId,
                                @Param("knowledgePointId") long knowledgePointId, @Param("scoreRate") String scoreRate,
                                @Param("direction") String direction, @Param("coefficient") String coefficient,
                                @Param("ruleVersionId") long ruleVersionId);
    void updateSettlementFinal(long settlementId);
    void aggregateSubjectProfiles(@Param("userId") long userId, @Param("goalId") long goalId,
                                  @Param("ruleVersionId") long ruleVersionId);
    void aggregateOverallProfile(@Param("userId") long userId, @Param("goalId") long goalId,
                                 @Param("ruleVersionId") long ruleVersionId);
    void insertReport(@Param("id") long id, @Param("session") FormalExamSessionRow session,
                      @Param("reportType") String reportType, @Param("totalScore") String totalScore,
                      @Param("maxScore") String maxScore, @Param("subjectScores") String subjectScores,
                      @Param("profileSummary") String profileSummary);
    String selectReport(@Param("userId") long userId, @Param("sessionId") long sessionId);
}
