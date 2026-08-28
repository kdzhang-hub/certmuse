package org.dromara.certmuse.assessment.domain.vo;

import java.time.OffsetDateTime;

/** Redacted acknowledgement of an exam-page lifecycle signal. */
public record PastPaperTimerEventVo(boolean eventAccepted, String eventType, long sessionVersion,
                                    OffsetDateTime deadlineTime, long remainingSeconds,
                                    OffsetDateTime serverTime) { }
