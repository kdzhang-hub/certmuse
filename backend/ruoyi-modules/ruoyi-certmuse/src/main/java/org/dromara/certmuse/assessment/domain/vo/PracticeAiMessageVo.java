package org.dromara.certmuse.assessment.domain.vo;

import java.time.OffsetDateTime;

/** Public persisted AI chat message. */
public record PracticeAiMessageVo(
    String id,
    int sequence,
    String role,
    String content,
    String status,
    String answerDisclosureMode,
    String errorCode,
    OffsetDateTime createdAt,
    OffsetDateTime completedAt
) {
}
