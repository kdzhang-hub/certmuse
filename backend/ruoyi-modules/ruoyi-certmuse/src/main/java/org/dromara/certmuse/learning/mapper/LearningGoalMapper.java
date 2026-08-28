package org.dromara.certmuse.learning.mapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.learning.domain.LearningGoalCertificationRow;
import org.dromara.certmuse.learning.domain.LearningGoalBatchRow;
import org.dromara.certmuse.learning.domain.LearningGoalIdempotencyRow;
import org.dromara.certmuse.learning.domain.LearningGoalRow;
import org.dromara.certmuse.learning.domain.LearningGoalSwitchSessionRow;

/** PostgreSQL persistence operations for first learning-goal creation. */
@Mapper
public interface LearningGoalMapper {
    Long selectCurrentGoalId(@Param("userId") long userId);
    Long lockCurrentGoalId(@Param("userId") long userId);
    List<LearningGoalCertificationRow> selectSelectableCertifications(@Param("today") LocalDate today);
    String selectCertificationStatus(@Param("certificationId") long certificationId);
    LearningGoalCertificationRow selectEnabledCertificationWithSyllabus(@Param("certificationId") long certificationId,
                                                                         @Param("today") LocalDate today);
    int insertGoal(@Param("id") long id, @Param("userId") long userId, @Param("certificationId") long certificationId,
                   @Param("syllabusVersionId") long syllabusVersionId, @Param("targetExamYear") int targetExamYear,
                   @Param("targetExamMonth") int targetExamMonth, @Param("dailyMinutes") int dailyMinutes,
                   @Param("examBatchType") String examBatchType, @Param("targetExamDate") LocalDate targetExamDate);
    LearningGoalBatchRow selectBatchSnapshot(@Param("certificationId") long certificationId,
                                             @Param("targetExamYear") int targetExamYear,
                                             @Param("targetExamMonth") int targetExamMonth,
                                             @Param("today") LocalDate today);
    List<LearningGoalBatchRow> selectFutureOfficialBatches(@Param("certificationId") long certificationId,
                                                            @Param("today") LocalDate today);
    LocalDate selectHistoricalOfficialExamDate(@Param("certificationId") long certificationId,
                                               @Param("targetExamYear") int targetExamYear,
                                               @Param("targetExamMonth") int targetExamMonth);
    int insertGoalChange(@Param("id") long id, @Param("goalId") long goalId, @Param("userId") long userId,
                         @Param("afterData") String afterData, @Param("requestId") String requestId,
                         @Param("operatorId") long operatorId);
    LearningGoalRow selectGoal(@Param("goalId") long goalId);
    LearningGoalIdempotencyRow selectIdempotency(@Param("action") String action, @Param("requestId") String requestId);
    int insertIdempotency(@Param("id") long id, @Param("action") String action, @Param("requestId") String requestId,
                          @Param("payloadHash") String payloadHash, @Param("resourceId") long resourceId,
                          @Param("expiresTime") OffsetDateTime expiresTime);
    int completeIdempotency(@Param("id") long id, @Param("responseBody") String responseBody);
    LearningGoalRow selectActiveGoal(@Param("userId") long userId);
    LearningGoalRow lockActiveGoal(@Param("userId") long userId);
    List<LearningGoalSwitchSessionRow> selectPendingSessions(@Param("goalId") long goalId);
    List<LearningGoalSwitchSessionRow> lockPendingSessions(@Param("goalId") long goalId);
    int cancelPendingSessions(@Param("goalId") long goalId, @Param("operatorId") long operatorId);
    int pauseGoal(@Param("goalId") long goalId, @Param("expectedVersion") long expectedVersion);
    int insertGoalSwitchChange(@Param("id") long id, @Param("goalId") long goalId, @Param("userId") long userId,
                               @Param("changeType") String changeType, @Param("beforeData") String beforeData,
                               @Param("afterData") String afterData, @Param("reason") String reason,
                               @Param("requestId") String requestId, @Param("operatorId") long operatorId);
}
