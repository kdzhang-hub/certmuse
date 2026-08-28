package org.dromara.certmuse.assessment.domain.vo;

public record DiagnosticDraftVo(String savedAt, boolean answered, int answeredCount, long sessionVersion) { }
