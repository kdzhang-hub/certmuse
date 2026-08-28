package org.dromara.certmuse.assessment.domain.vo;

/** Redacted timing acknowledgement; clients derive display-only countdowns locally. */
public record DiagnosticTimerEventVo(boolean eventAccepted, String leaseId, int questionOrder,
                                     String timerState, int estimatedDurationSeconds,
                                     long effectiveElapsedSeconds, String serverTime) { }
