package org.dromara.certmuse.assessment.domain;

import java.time.OffsetDateTime;
import lombok.Data;

/** Persistence projection for an owned formal-exam session. */
@Data
public class FormalExamSessionRow {
    private Long id;
    private Long userId;
    private Long goalId;
    private Long collectionRevisionId;
    private Long ruleVersionId;
    private String sessionType;
    private String title;
    private String status;
    private Long rowVersion;
    private Integer lastQuestionOrder;
    private Integer durationSecondsSnapshot;
    private OffsetDateTime startedTime;
    private OffsetDateTime submittedTime;
    private Integer formalAttemptNo;
    private Integer totalCount;
    private Integer answeredCount;
}
