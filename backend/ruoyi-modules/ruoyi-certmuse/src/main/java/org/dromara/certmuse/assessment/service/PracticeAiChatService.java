package org.dromara.certmuse.assessment.service;

import org.dromara.certmuse.assessment.domain.bo.SendPracticeAiMessageBo;
import org.dromara.certmuse.assessment.domain.vo.CancelPracticeAiMessageVo;
import org.dromara.certmuse.assessment.domain.vo.CreatePracticeAiConversationVo;
import org.dromara.certmuse.assessment.domain.vo.PracticeAiConversationVo;
import org.dromara.certmuse.assessment.domain.vo.PracticeAiMessagePageVo;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Learner question AI conversation use cases. */
public interface PracticeAiChatService {
    CreatePracticeAiConversationVo create(long userId, long sessionId, int questionOrder, String requestId);
    PracticeAiConversationVo conversation(long userId, long conversationId);
    PracticeAiMessagePageVo messages(long userId, long conversationId, Integer beforeSequence, int limit);
    SseEmitter stream(long userId, long conversationId, String requestId, SendPracticeAiMessageBo command);
    CancelPracticeAiMessageVo cancel(long userId, long conversationId, long messageId, String requestId);
}
