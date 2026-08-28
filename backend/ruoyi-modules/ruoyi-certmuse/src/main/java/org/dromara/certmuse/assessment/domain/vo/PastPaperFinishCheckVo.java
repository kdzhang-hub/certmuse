package org.dromara.certmuse.assessment.domain.vo;

import java.time.OffsetDateTime;

/** Server-authoritative pre-finish summary for a timed past-paper exam. */
public record PastPaperFinishCheckVo(long sessionVersion, int unansweredCount, int totalCount,
                                     OffsetDateTime deadlineTime, boolean canFinish) { }
