package org.dromara.certmuse.assessment.domain.vo;

public record DiagnosticStartVo(String sessionId, String status, boolean resumed, int answeredCount,
                                int totalCount, int resumeQuestionOrder, long sessionVersion) { }
