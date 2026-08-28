package org.dromara.certmuse.assessment.domain;

import java.time.OffsetDateTime;
import lombok.Data;

/** Owned question-level AI conversation persistence projection. */
@Data
public class PracticeAiConversationRow {
    private Long id;
    private Long userId;
    private Long practiceSessionId;
    private Integer questionOrder;
    private String status;
    private String provider;
    private String modelName;
    private OffsetDateTime createTime;
    private OffsetDateTime updateTime;
    private Long generatingAssistantMessageId;
}
