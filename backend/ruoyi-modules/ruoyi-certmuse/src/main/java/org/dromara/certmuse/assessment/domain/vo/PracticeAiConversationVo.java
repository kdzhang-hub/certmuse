package org.dromara.certmuse.assessment.domain.vo;

import java.time.OffsetDateTime;

/** Public question AI conversation summary. */
public record PracticeAiConversationVo(
    String conversationId,
    String practiceSessionId,
    int questionOrder,
    String status,
    String answerDisclosureMode,
    String generatingAssistantMessageId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
