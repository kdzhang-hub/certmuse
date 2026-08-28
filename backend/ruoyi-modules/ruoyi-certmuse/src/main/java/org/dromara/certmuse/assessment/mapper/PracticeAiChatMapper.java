package org.dromara.certmuse.assessment.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.assessment.domain.PracticeAiConversationRow;
import org.dromara.certmuse.assessment.domain.PracticeAiIdempotencyRow;
import org.dromara.certmuse.assessment.domain.PracticeAiMessageRow;
import org.dromara.certmuse.assessment.domain.PracticeAiQuestionContextRow;

/** PostgreSQL persistence for owned question AI conversations and messages. */
public interface PracticeAiChatMapper {
    PracticeAiQuestionContextRow selectQuestionContext(@Param("sessionId") long sessionId,
                                                        @Param("userId") long userId,
                                                        @Param("questionOrder") int questionOrder);
    PracticeAiConversationRow selectConversationByItem(@Param("userId") long userId,
                                                        @Param("sessionId") long sessionId,
                                                        @Param("questionOrder") int questionOrder);
    PracticeAiConversationRow selectOwnedConversation(@Param("conversationId") long conversationId,
                                                       @Param("userId") long userId);
    PracticeAiConversationRow lockOwnedConversation(@Param("conversationId") long conversationId,
                                                     @Param("userId") long userId);
    int insertConversation(@Param("id") long id, @Param("userId") long userId,
                           @Param("sessionId") long sessionId, @Param("questionOrder") int questionOrder,
                           @Param("provider") String provider, @Param("modelName") String modelName);
    int countMessages(long conversationId);
    List<PracticeAiMessageRow> selectMessages(@Param("conversationId") long conversationId,
                                              @Param("beforeSequence") Integer beforeSequence,
                                              @Param("limit") int limit);
    List<PracticeAiMessageRow> selectRecentMessages(@Param("conversationId") long conversationId,
                                                    @Param("limit") int limit);
    PracticeAiMessageRow selectUserMessageByClientId(String clientMessageId);
    PracticeAiMessageRow selectAssistantReply(long userMessageId);
    PracticeAiMessageRow selectOwnedAssistantMessage(@Param("conversationId") long conversationId,
                                                      @Param("messageId") long messageId,
                                                      @Param("userId") long userId);
    int insertUserMessage(@Param("id") long id, @Param("conversationId") long conversationId,
                          @Param("sequence") int sequence, @Param("content") String content,
                          @Param("clientMessageId") String clientMessageId);
    int insertAssistantMessage(@Param("id") long id, @Param("conversationId") long conversationId,
                               @Param("userMessageId") long userMessageId,
                               @Param("sequence") int sequence, @Param("disclosureMode") String disclosureMode);
    int completeAssistantMessage(@Param("id") long id, @Param("content") String content,
                                 @Param("citations") String citations);
    int failAssistantMessage(@Param("id") long id, @Param("errorCode") String errorCode);
    int cancelAssistantMessage(long id);
    List<PracticeAiMessageRow> selectStaleGenerating(int seconds);
    int failStaleGenerating(@Param("id") long id, @Param("errorCode") String errorCode);
    PracticeAiIdempotencyRow selectIdempotency(@Param("actionCode") String actionCode,
                                                @Param("requestId") String requestId);
    int insertIdempotency(@Param("id") long id, @Param("actionCode") String actionCode,
                          @Param("requestId") String requestId, @Param("payloadHash") String payloadHash);
    int succeedIdempotency(@Param("id") long id, @Param("resourceType") String resourceType,
                           @Param("resourceId") long resourceId, @Param("responseBody") String responseBody);
}
