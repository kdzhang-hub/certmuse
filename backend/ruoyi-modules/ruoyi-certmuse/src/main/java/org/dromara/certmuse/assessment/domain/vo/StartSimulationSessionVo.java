package org.dromara.certmuse.assessment.domain.vo;

import java.time.OffsetDateTime;

/** Future success payload retained by U13 while the formal-exam gate is closed. */
public record StartSimulationSessionVo(
    OffsetDateTime serverTime,
    String sessionId,
    String collectionId,
    String revisionId,
    boolean resumed,
    int formalAttemptNo,
    boolean isRetest,
    OffsetDateTime startedTime,
    int durationSeconds,
    OffsetDateTime deadlineTime,
    String answerPath
) {
}
