package org.dromara.certmuse.assessment.domain.vo;

/** Acknowledged formal-exam timing event. */
public record FormalExamTimerEventVo(
    boolean eventAccepted,
    String leaseId,
    int questionOrder,
    String timerState,
    int estimatedDurationSeconds,
    int effectiveElapsedSeconds,
    String serverTime
) { }
