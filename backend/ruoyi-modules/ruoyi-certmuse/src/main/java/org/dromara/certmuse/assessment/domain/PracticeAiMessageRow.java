package org.dromara.certmuse.assessment.domain;

import java.time.OffsetDateTime;
import lombok.Data;

/** Persisted AI chat message projection. */
@Data
public class PracticeAiMessageRow {
    private Long id;
    private Long conversationId;
    private Long replyToMessageId;
    private Integer sequenceNo;
    private String role;
    private String content;
    private String status;
    private String answerDisclosureMode;
    private String errorCode;
    private String clientMessageId;
    private String citations;
    private OffsetDateTime createTime;
    private OffsetDateTime completedTime;
    private Long ownerUserId;
}
