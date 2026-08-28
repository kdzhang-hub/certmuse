package org.dromara.certmuse.assessment.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.assessment.domain.DiagnosticCountRow;
import org.dromara.certmuse.assessment.domain.DiagnosticGoalRow;
import org.dromara.certmuse.assessment.domain.DiagnosticIdempotencyRow;
import org.dromara.certmuse.assessment.domain.DiagnosticItemRow;
import org.dromara.certmuse.assessment.domain.DiagnosticJobRow;
import org.dromara.certmuse.assessment.domain.DiagnosticLeafRow;
import org.dromara.certmuse.assessment.domain.DiagnosticReportRow;
import org.dromara.certmuse.assessment.domain.DiagnosticRevisionRow;
import org.dromara.certmuse.assessment.domain.DiagnosticSessionRow;
import org.dromara.certmuse.assessment.domain.DiagnosticTimerLeaseRow;
import org.dromara.certmuse.assessment.domain.DiagnosticTimingRow;
import java.util.List;

/** PostgreSQL persistence operations requiring locks and aggregate projections. */
public interface DiagnosticMapper {
    DiagnosticGoalRow selectActiveGoal(long userId);
    DiagnosticSessionRow selectLatestSession(@Param("userId") long userId, @Param("goalId") long goalId);
    DiagnosticSessionRow lockSession(@Param("userId") long userId, @Param("sessionId") long sessionId);
    DiagnosticSessionRow lockSessionForWorker(long sessionId);
    DiagnosticRevisionRow selectAvailableRevision(@Param("certificationId") long certificationId, @Param("syllabusVersionId") long syllabusVersionId);
    List<DiagnosticItemRow> selectRevisionItems(long revisionId);
    List<DiagnosticItemRow> selectSessionItems(@Param("userId") long userId, @Param("sessionId") long sessionId);
    DiagnosticItemRow selectSessionItem(@Param("userId") long userId, @Param("sessionId") long sessionId, @Param("order") int order);
    DiagnosticCountRow countAnswers(@Param("userId") long userId, @Param("sessionId") long sessionId);
    int insertSession(@Param("id") long id, @Param("userId") long userId, @Param("goal") DiagnosticGoalRow goal, @Param("revision") DiagnosticRevisionRow revision, @Param("ruleVersionId") long ruleVersionId, @Param("requestId") String requestId);
    void insertSessionQuestion(@Param("id") long id, @Param("sessionId") long sessionId, @Param("item") DiagnosticItemRow item);
    void insertAttempt(@Param("id") long id, @Param("sessionQuestionId") long sessionQuestionId, @Param("userId") long userId, @Param("requestId") String requestId);
    void insertAnswer(@Param("id") long id, @Param("attemptId") long attemptId, @Param("answer") String answer);
    int updateAnswer(@Param("answerId") long answerId, @Param("answer") String answer);
    int updateSessionPosition(@Param("sessionId") long sessionId, @Param("expected") long expected, @Param("order") int order);
    int markPresented(@Param("attemptId") long attemptId);
    DiagnosticTimingRow selectTiming(@Param("userId") long userId, @Param("sessionId") long sessionId);
    DiagnosticTimerLeaseRow lockTimerLease(long sessionId);
    void deleteTimerLease(long sessionId);
    void insertTimerLease(@Param("sessionId") long sessionId, @Param("questionOrder") int questionOrder,
                          @Param("attemptId") long attemptId, @Param("leaseId") String leaseId);
    int accumulateTimerInterval(@Param("attemptId") long attemptId, @Param("seconds") int seconds);
    void markTimerInvalid(@Param("attemptId") long attemptId, @Param("reason") String reason);
    void activateTimer(@Param("attemptId") long attemptId);
    int submitSession(@Param("sessionId") long sessionId, @Param("expected") long expected);
    void submitAttempts(long sessionId);
    void insertJob(@Param("id") long id, @Param("businessKey") String businessKey, @Param("payload") String payload);
    DiagnosticJobRow selectJob(@Param("businessKey") String businessKey);
    int retryJob(@Param("businessKey") String businessKey);
    DiagnosticReportRow selectReport(@Param("userId") long userId, @Param("sessionId") long sessionId);
    int countSessionProfileEvidence(long sessionId);
    DiagnosticIdempotencyRow selectDiagnosticIdempotency(@Param("requestId") String requestId);
    int insertIdempotency(@Param("id") long id, @Param("action") String action, @Param("requestId") String requestId, @Param("hash") String hash);
    void completeIdempotency(@Param("id") long id, @Param("body") String body);
    DiagnosticJobRow claimResultJob();
    void succeedJob(long id);
    void failJob(@Param("id") long id, @Param("error") String error);
    List<DiagnosticItemRow> selectSubmittedItems(long sessionId);
    List<DiagnosticLeafRow> selectLeafCatalog(long sessionId);
    void scoreAnswer(@Param("answerId") long answerId, @Param("score") java.math.BigDecimal score,
                     @Param("max") java.math.BigDecimal max, @Param("rate") java.math.BigDecimal rate);
    void scoreSubjectiveAnswer(@Param("answerId") long answerId, @Param("score") java.math.BigDecimal score,
                               @Param("max") java.math.BigDecimal max, @Param("rate") java.math.BigDecimal rate,
                               @Param("result") String result);
    void failSubjectiveAnswer(@Param("answerId") long answerId, @Param("max") java.math.BigDecimal max,
                              @Param("result") String result);
    void updateAiRubricSnapshot(@Param("sessionQuestionId") long sessionQuestionId, @Param("rubric") String rubric);
    void upsertReport(@Param("id") long id, @Param("sessionId") long sessionId, @Param("userId") long userId,
                      @Param("goalId") long goalId, @Param("ruleVersionId") long ruleVersionId,
                      @Param("total") java.math.BigDecimal total, @Param("max") java.math.BigDecimal max,
                      @Param("subjects") String subjects, @Param("summary") String summary);
    long upsertOverallProfile(@Param("id") long id, @Param("userId") long userId, @Param("goalId") long goalId,
                              @Param("ruleVersionId") long ruleVersionId, @Param("ability") java.math.BigDecimal ability,
                              @Param("snapshot") String snapshot);
    void settleSession(long sessionId);
    void completeSession(long sessionId);
    void advanceJobStage(@Param("id") long id, @Param("stage") String stage);
    void requeueJob(@Param("id") long id, @Param("stage") String stage);
    long upsertKnowledgeProfile(@Param("id") long id, @Param("userId") long userId, @Param("goalId") long goalId,
                                @Param("knowledgePointId") long knowledgePointId, @Param("ability") java.math.BigDecimal ability,
                                @Param("count") int count, @Param("confidence") String confidence, @Param("status") String status, @Param("ruleVersionId") long ruleVersionId);
    long upsertSubjectProfile(@Param("id") long id, @Param("userId") long userId, @Param("goalId") long goalId,
                              @Param("subjectId") long subjectId, @Param("ability") java.math.BigDecimal ability,
                              @Param("targetCoverage") java.math.BigDecimal targetCoverage,
                              @Param("mediumHighCoverage") java.math.BigDecimal mediumHighCoverage,
                              @Param("coreCovered") boolean coreCovered, @Param("confidence") String confidence,
                              @Param("efficiencyStatus") String efficiencyStatus, @Param("efficiencyCount") int efficiencyCount,
                              @Param("timeRisk") boolean timeRisk, @Param("guessingRisk") boolean guessingRisk,
                              @Param("ruleVersionId") long ruleVersionId);
    long insertSettlement(@Param("id") long id, @Param("userId") long userId, @Param("goalId") long goalId,
                          @Param("subjectId") long subjectId, @Param("knowledgePointId") long knowledgePointId,
                          @Param("sessionId") long sessionId, @Param("ability") java.math.BigDecimal ability,
                          @Param("count") int count, @Param("ruleVersionId") long ruleVersionId, @Param("requestId") String requestId);
    void insertEvidence(@Param("id") long id, @Param("settlementId") long settlementId, @Param("userId") long userId,
                        @Param("goalId") long goalId, @Param("sessionId") long sessionId, @Param("attemptId") long attemptId,
                        @Param("subjectId") long subjectId, @Param("knowledgePointId") long knowledgePointId,
                        @Param("groupKey") String groupKey, @Param("difficulty") String difficulty,
                        @Param("scoreRate") java.math.BigDecimal scoreRate, @Param("raw") java.math.BigDecimal raw,
                        @Param("direction") String direction, @Param("elapsed") Integer elapsed, @Param("timerStatus") String timerStatus,
                        @Param("timeCoefficient") java.math.BigDecimal timeCoefficient, @Param("valid") boolean valid,
                        @Param("ruleVersionId") long ruleVersionId, @Param("sequence") long sequence);
    void insertKnowledgeChange(@Param("id") long id, @Param("knowledgeProfileId") long knowledgeProfileId,
                               @Param("settlementId") long settlementId, @Param("ruleVersionId") long ruleVersionId,
                               @Param("requestId") String requestId, @Param("afterData") String afterData);
    void insertSubjectChange(@Param("id") long id, @Param("subjectProfileId") long subjectProfileId,
                             @Param("ruleVersionId") long ruleVersionId, @Param("requestId") String requestId,
                             @Param("afterData") String afterData);
    void insertOverallChange(@Param("id") long id, @Param("overallProfileId") long overallProfileId,
                             @Param("ruleVersionId") long ruleVersionId, @Param("requestId") String requestId,
                             @Param("afterData") String afterData);
}
