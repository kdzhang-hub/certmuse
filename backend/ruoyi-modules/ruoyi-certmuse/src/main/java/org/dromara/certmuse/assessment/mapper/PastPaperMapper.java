package org.dromara.certmuse.assessment.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.assessment.domain.PastPaperGoalRow;
import org.dromara.certmuse.assessment.domain.PastPaperIdempotencyRow;
import org.dromara.certmuse.assessment.domain.PastPaperPaperRow;
import org.dromara.certmuse.assessment.domain.PastPaperQuestionRow;
import org.dromara.certmuse.assessment.domain.PastPaperSessionRow;

/** PostgreSQL persistence for the U15 immutable-paper aggregate. */
public interface PastPaperMapper {
    List<PastPaperPaperRow> selectPapers(@Param("userId") Long userId, @Param("certificationId") Long certificationId,
                                         @Param("subjectId") Long subjectId, @Param("keyword") String keyword,
                                         @Param("limit") int limit, @Param("offset") long offset);
    long countPapers(@Param("certificationId") Long certificationId, @Param("subjectId") Long subjectId,
                     @Param("keyword") String keyword);
    PastPaperPaperRow selectPaper(long collectionId);
    List<PastPaperQuestionRow> selectPublishedQuestions(long collectionId);
    PastPaperQuestionRow selectPublishedQuestion(@Param("collectionId") long collectionId,
                                                  @Param("questionOrder") int questionOrder);
    PastPaperGoalRow selectActiveGoal(long userId);
    Long selectPublishedRuleVersion();
    PastPaperIdempotencyRow selectIdempotency(@Param("action") String action, @Param("requestId") String requestId);
    int insertIdempotency(@Param("id") long id, @Param("action") String action, @Param("requestId") String requestId,
                          @Param("payloadHash") String payloadHash);
    void completeIdempotency(@Param("id") long id, @Param("resourceType") String resourceType,
                             @Param("resourceId") long resourceId, @Param("responseBody") String responseBody);
    PastPaperSessionRow selectActiveSession(@Param("userId") long userId, @Param("goalId") long goalId,
                                            @Param("revisionId") long revisionId, @Param("sessionType") String sessionType);
    int nextFormalAttemptNo(@Param("userId") long userId, @Param("goalId") long goalId, @Param("revisionId") long revisionId);
    int insertSession(@Param("id") long id, @Param("userId") long userId, @Param("goal") PastPaperGoalRow goal,
                      @Param("revisionId") long revisionId, @Param("sessionType") String sessionType,
                      @Param("ruleVersionId") long ruleVersionId, @Param("requestId") String requestId,
                      @Param("durationSeconds") Integer durationSeconds, @Param("formalAttemptNo") Integer formalAttemptNo);
    void insertSessionQuestion(@Param("id") long id, @Param("sessionId") long sessionId,
                               @Param("question") PastPaperQuestionRow question, @Param("presentation") String presentation,
                               @Param("grading") String grading, @Param("knowledge") String knowledge);
    PastPaperSessionRow selectSession(@Param("userId") long userId, @Param("sessionId") long sessionId);
    PastPaperSessionRow lockSession(@Param("userId") long userId, @Param("sessionId") long sessionId);
    PastPaperSessionRow lockSessionForWorker(long sessionId);
    List<PastPaperQuestionRow> selectSessionQuestions(@Param("userId") long userId, @Param("sessionId") long sessionId);
    PastPaperQuestionRow selectSessionQuestion(@Param("userId") long userId, @Param("sessionId") long sessionId,
                                               @Param("questionOrder") int questionOrder);
    List<PastPaperQuestionRow> selectPendingPracticeSubjectiveAttempts(@Param("limit") int limit);
    int saveDraft(@Param("sessionQuestionId") long sessionQuestionId, @Param("userId") long userId,
                  @Param("answerData") String answerData);
    int incrementSessionVersion(@Param("sessionId") long sessionId, @Param("expectedVersion") long expectedVersion);
    int insertAttempt(@Param("id") long id, @Param("sessionQuestionId") long sessionQuestionId, @Param("userId") long userId,
                      @Param("requestId") String requestId, @Param("blank") boolean blank, @Param("skipped") boolean skipped,
                      @Param("firstAttempt") boolean firstAttempt, @Param("repeatAttempt") boolean repeatAttempt,
                      @Param("profileApplied") boolean profileApplied);
    void insertAttemptAnswer(@Param("id") long id, @Param("attemptId") long attemptId, @Param("answerData") String answerData,
                             @Param("score") String score, @Param("maxScore") String maxScore, @Param("scoreRate") String scoreRate);
    void insertPendingSubjectiveAttemptAnswer(@Param("id") long id, @Param("attemptId") long attemptId,
                                              @Param("answerData") String answerData, @Param("maxScore") String maxScore);
    void updateAiRubricSnapshot(@Param("sessionQuestionId") long sessionQuestionId, @Param("rubric") String rubric);
    void scoreSubjectiveAttempt(@Param("attemptId") long attemptId, @Param("score") String score,
                                @Param("maxScore") String maxScore, @Param("scoreRate") String scoreRate,
                                @Param("result") String result);
    void failSubjectiveAttempt(@Param("attemptId") long attemptId, @Param("maxScore") String maxScore,
                               @Param("result") String result);
    int markSubmitted(@Param("sessionId") long sessionId, @Param("expectedVersion") Long expectedVersion);
    int markSettling(long sessionId);
    int markCompleted(long sessionId);
    int insertJob(@Param("id") long id, @Param("businessKey") String businessKey, @Param("payload") String payload);
    String selectLatestDisclosure(@Param("userId") long userId, @Param("questionId") long questionId);
    void insertDisclosure(@Param("id") long id, @Param("userId") long userId, @Param("question") PastPaperQuestionRow question,
                          @Param("collectionRevisionId") long collectionRevisionId, @Param("requestId") String requestId);
    List<PastPaperSessionRow> lockExpiredExamSessions(@Param("limit") int limit);
    String selectJobStatus(@Param("businessKey") String businessKey);
    List<Long> selectQueuedResultSessionIds(@Param("limit") int limit);
    int claimJob(@Param("businessKey") String businessKey);
    void markJobSucceeded(@Param("businessKey") String businessKey);
    void markJobFailed(@Param("businessKey") String businessKey, @Param("message") String message);
    void retryJob(@Param("businessKey") String businessKey);
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
                          @Param("requestId") String requestId);
    void insertEvidence(@Param("id") long id, @Param("settlementId") long settlementId,
                        @Param("userId") long userId, @Param("goalId") long goalId,
                        @Param("sessionId") long sessionId, @Param("attemptId") long attemptId,
                        @Param("subjectId") long subjectId, @Param("knowledgePointId") long knowledgePointId,
                        @Param("groupKey") String groupKey, @Param("behaviorType") String behaviorType,
                        @Param("difficulty") String difficulty, @Param("scoreRate") String scoreRate,
                        @Param("direction") String direction, @Param("coefficient") String coefficient,
                        @Param("distinct") boolean distinct, @Param("ruleVersionId") long ruleVersionId);
    void upsertKnowledgeProfile(@Param("id") long id, @Param("userId") long userId, @Param("goalId") long goalId,
                                @Param("knowledgePointId") long knowledgePointId, @Param("correct") boolean correct,
                                @Param("coefficient") String coefficient, @Param("ruleVersionId") long ruleVersionId);
    void upsertScoredKnowledgeProfile(@Param("id") long id, @Param("userId") long userId, @Param("goalId") long goalId,
                                      @Param("knowledgePointId") long knowledgePointId, @Param("scoreRate") String scoreRate,
                                      @Param("coefficient") String coefficient, @Param("ruleVersionId") long ruleVersionId);
    void updateSettlementFinal(long settlementId);
    void aggregateSubjectProfiles(@Param("userId") long userId, @Param("goalId") long goalId,
                                  @Param("ruleVersionId") long ruleVersionId);
    void aggregateOverallProfile(@Param("userId") long userId, @Param("goalId") long goalId,
                                 @Param("ruleVersionId") long ruleVersionId);
    void insertReport(@Param("id") long id, @Param("session") PastPaperSessionRow session,
                      @Param("reportType") String reportType, @Param("totalScore") String totalScore,
                      @Param("maxScore") String maxScore, @Param("subjectScores") String subjectScores,
                      @Param("profileSummary") String profileSummary);
    String selectReport(@Param("userId") long userId, @Param("sessionId") long sessionId);
}
