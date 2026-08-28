package org.dromara.certmuse.learning.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.learning.domain.MistakeErrorRow;
import org.dromara.certmuse.learning.domain.MistakeIdempotencyRow;
import org.dromara.certmuse.learning.domain.MistakeSessionItemRow;
import org.dromara.certmuse.learning.domain.MistakeSessionRow;

/** PostgreSQL persistence for frozen mistake-review operations. */
public interface MistakeReviewMapper {
    Long selectActiveGoal(long userId);
    Long lockActiveGoal(long userId);
    Long selectUniquePublishedRuleVersion();
    List<MistakeErrorRow> selectErrors(@Param("userId") long userId, @Param("goalId") long goalId);
    MistakeSessionRow selectActiveSession(@Param("userId") long userId, @Param("goalId") long goalId);
    MistakeSessionRow selectOwnedSession(@Param("sessionId") long sessionId, @Param("userId") long userId);
    MistakeSessionRow lockOwnedSession(@Param("sessionId") long sessionId, @Param("userId") long userId);
    int startSession(@Param("sessionId") long sessionId, @Param("userId") long userId);
    int insertSession(@Param("id") long id, @Param("userId") long userId, @Param("goalId") long goalId,
                      @Param("ruleVersionId") long ruleVersionId, @Param("requestId") String requestId);
    void insertSessionQuestion(@Param("id") long id, @Param("sessionId") long sessionId, @Param("source") MistakeErrorRow source,
                               @Param("questionOrder") int questionOrder);
    MistakeSessionItemRow selectItem(@Param("sessionId") long sessionId, @Param("userId") long userId, @Param("questionOrder") int questionOrder);
    List<MistakeSessionItemRow> selectItems(@Param("sessionId") long sessionId, @Param("userId") long userId);
    int insertAttempt(@Param("id") long id, @Param("item") MistakeSessionItemRow item, @Param("requestId") String requestId);
    void insertAttemptAnswer(@Param("id") long id, @Param("attemptId") long attemptId, @Param("answerData") String answerData,
                             @Param("correct") boolean correct);
    void insertPendingSubjectiveAttemptAnswer(@Param("id") long id, @Param("attemptId") long attemptId,
                                              @Param("answerData") String answerData);
    void scoreSubjectiveAttempt(@Param("attemptId") long attemptId, @Param("scoreRate") String scoreRate,
                                @Param("result") String result);
    void failSubjectiveAttempt(@Param("attemptId") long attemptId, @Param("result") String result);
    List<Long> lockPendingErrorIds(@Param("userId") long userId, @Param("goalId") long goalId, @Param("questionId") long questionId);
    void insertCorrection(@Param("id") long id, @Param("errorRecordId") long errorRecordId, @Param("attemptId") long attemptId,
                          @Param("requestId") String requestId);
    void closeError(@Param("errorRecordId") long errorRecordId, @Param("userId") long userId);
    int countSubmitted(@Param("sessionId") long sessionId);
    int countPendingGrading(@Param("sessionId") long sessionId);
    int submitSession(@Param("sessionId") long sessionId, @Param("userId") long userId);
    int settleSession(@Param("sessionId") long sessionId, @Param("userId") long userId);
    int completeSession(@Param("sessionId") long sessionId, @Param("userId") long userId);
    MistakeIdempotencyRow selectIdempotency(@Param("actionCode") String actionCode, @Param("requestId") String requestId);
    int insertIdempotency(@Param("id") long id, @Param("actionCode") String actionCode, @Param("requestId") String requestId,
                          @Param("payloadHash") String payloadHash);
    void completeIdempotency(@Param("id") long id, @Param("resourceId") long resourceId, @Param("responseBody") String responseBody);
}
