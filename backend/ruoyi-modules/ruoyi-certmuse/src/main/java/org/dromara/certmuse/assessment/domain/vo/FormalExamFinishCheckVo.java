package org.dromara.certmuse.assessment.domain.vo;

/** Server-authoritative summary shown before formal submission. */
public record FormalExamFinishCheckVo(long sessionVersion, int answeredCount, int unansweredCount,
                                      int totalCount, boolean canFinish) { }
