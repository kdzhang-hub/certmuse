package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

public record DiagnosticStatusVo(String sessionId, String diagnosticStatus, String nextAction,
                                 List<StageVo> stages, FailureVo failure) {
    public record StageVo(String code, String state) { }
    public record FailureVo(String message, String traceId, boolean retryAvailable) { }
}
