package org.dromara.certmuse.assessment.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeAnsweringSessionRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeIdempotencyRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeItemRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeNavigationRow;

/** PostgreSQL persistence restricted to owned daily-task sessions. */
public interface DailyTaskMapper {
    KnowledgePracticeAnsweringSessionRow selectSession(@Param("sessionId") long sessionId, @Param("userId") long userId);
    KnowledgePracticeAnsweringSessionRow lockSession(@Param("sessionId") long sessionId, @Param("userId") long userId);
    List<KnowledgePracticeNavigationRow> selectNavigation(long sessionId);
    KnowledgePracticeItemRow selectItem(@Param("sessionId") long sessionId, @Param("userId") long userId,
                                         @Param("questionOrder") int questionOrder);
    KnowledgePracticeIdempotencyRow selectIdempotency(@Param("actionCode") String actionCode,
                                                       @Param("requestId") String requestId);
    int insertIdempotency(@Param("id") long id, @Param("actionCode") String actionCode,
                          @Param("requestId") String requestId, @Param("payloadHash") String payloadHash);
    void succeedIdempotency(@Param("id") long id, @Param("resourceType") String resourceType,
                            @Param("resourceId") long resourceId, @Param("responseBody") String responseBody);
    int insertAttempt(@Param("id") long id, @Param("sessionQuestionId") long sessionQuestionId,
                      @Param("userId") long userId, @Param("requestId") String requestId);
    void insertAttemptAnswer(@Param("id") long id, @Param("attemptId") long attemptId,
                             @Param("answerData") String answerData, @Param("correct") boolean correct);
    int countSubmittedItems(long sessionId);
    int submitSession(@Param("sessionId") long sessionId, @Param("userId") long userId);
    int settleSession(@Param("sessionId") long sessionId, @Param("userId") long userId);
    int completeSession(@Param("sessionId") long sessionId, @Param("userId") long userId);
}
