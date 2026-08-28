package org.dromara.certmuse.assessment.domain.vo;

public record DiagnosticFinishCheckVo(int answeredCount, int unansweredCount, int pendingSaveCount,
                                      long sessionVersion, Integer firstUnansweredQuestionOrder) { }
