package org.dromara.certmuse.assessment.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.assessment.domain.SimulationGoalRow;
import org.dromara.certmuse.assessment.domain.SimulationOptionRow;
import org.dromara.certmuse.assessment.domain.SimulationPaperRow;
import org.dromara.certmuse.assessment.domain.SimulationPreviewQuestionRow;
import org.dromara.certmuse.assessment.domain.SimulationSessionQuestionRow;
import org.dromara.certmuse.assessment.domain.SimulationAttemptRow;

/** PostgreSQL reads for current published learner simulations. */
public interface SimulationMapper {
    SimulationGoalRow selectCurrentGoal(long userId);

    List<SimulationOptionRow> selectSetupOptions();

    List<SimulationPaperRow> selectPublishedSimulations(
        @Param("userId") long userId,
        @Param("certificationId") Long certificationId,
        @Param("syllabusVersionId") Long syllabusVersionId,
        @Param("keyword") String keyword,
        @Param("limit") int limit,
        @Param("offset") long offset
    );

    long countPublishedSimulations(
        @Param("certificationId") Long certificationId,
        @Param("syllabusVersionId") Long syllabusVersionId,
        @Param("keyword") String keyword
    );

    SimulationPaperRow selectPublishedSimulation(@Param("userId") long userId, @Param("collectionId") long collectionId);

    List<SimulationPreviewQuestionRow> selectPublishedSimulationPreviewQuestions(long collectionId);
    SimulationGoalRow lockCurrentGoal(long userId);
    Long selectPublishedRuleVersion();
    List<SimulationSessionQuestionRow> selectPublishedSimulationSessionQuestions(long collectionId);
    int nextFormalAttemptNo(@Param("userId") long userId, @Param("goalId") long goalId, @Param("revisionId") long revisionId);
    void insertSession(@Param("id") long id, @Param("userId") long userId, @Param("goal") SimulationGoalRow goal,
                       @Param("revisionId") long revisionId, @Param("ruleVersionId") long ruleVersionId,
                       @Param("requestId") String requestId, @Param("durationSeconds") int durationSeconds,
                       @Param("formalAttemptNo") int formalAttemptNo);
    void insertSessionQuestion(@Param("id") long id, @Param("sessionId") long sessionId,
                               @Param("question") SimulationSessionQuestionRow question, @Param("presentation") String presentation,
                               @Param("grading") String grading, @Param("knowledge") String knowledge);
    SimulationAttemptRow selectSession(@Param("userId") long userId, @Param("sessionId") long sessionId);
    List<SimulationAttemptRow> selectSessionItems(@Param("userId") long userId, @Param("sessionId") long sessionId);
    SimulationAttemptRow selectSessionItem(@Param("userId") long userId, @Param("sessionId") long sessionId, @Param("questionOrder") int questionOrder);
    SimulationAttemptRow lockSession(@Param("userId") long userId, @Param("sessionId") long sessionId);
    int insertAttempt(@Param("id") long id, @Param("sessionQuestionId") long sessionQuestionId, @Param("userId") long userId, @Param("requestId") String requestId);
    void insertAttemptAnswer(@Param("id") long id, @Param("attemptId") long attemptId, @Param("answerData") String answerData, @Param("scoreRate") String scoreRate, @Param("status") String status);
    void scoreSubjectiveAttempt(@Param("attemptId") long attemptId, @Param("scoreRate") String scoreRate, @Param("result") String result);
    void failSubjectiveAttempt(@Param("attemptId") long attemptId, @Param("result") String result);
    int markSubmitted(@Param("sessionId") long sessionId, @Param("userId") long userId);
    int markCompleted(@Param("sessionId") long sessionId, @Param("userId") long userId);
    void insertReport(@Param("id") long id, @Param("sessionId") long sessionId, @Param("userId") long userId,
                      @Param("score") String score, @Param("maxScore") String maxScore, @Param("partial") boolean partial);
    SimulationAttemptRow selectResult(@Param("userId") long userId, @Param("sessionId") long sessionId);
    void insertErrorRecord(@Param("id") long id, @Param("userId") long userId, @Param("goalId") long goalId,
                           @Param("attemptId") long attemptId, @Param("knowledgePointId") long knowledgePointId,
                           @Param("groupKey") String groupKey);
}
