package org.dromara.certmuse.assessment.domain.vo;

/** Idempotent conversation creation result. */
public record CreatePracticeAiConversationVo(boolean created, PracticeAiConversationVo conversation) {
}
