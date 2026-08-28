package org.dromara.certmuse.assessment.domain;

import java.time.OffsetDateTime;
import lombok.Data;

/** Owned past-paper session state used by the service state machine. */
@Data
public class PastPaperSessionRow {
    private Long id;
    private Long userId;
    private Long goalId;
    private Long collectionRevisionId;
    private Long ruleVersionId;
    private String sessionType;
    private String status;
    private Long rowVersion;
    private Integer totalCount;
    private Integer answeredCount;
    private Integer durationSecondsSnapshot;
    private OffsetDateTime startedTime;
    private OffsetDateTime submittedTime;
    private OffsetDateTime deadlineTime;
    private Integer formalAttemptNo;
}
