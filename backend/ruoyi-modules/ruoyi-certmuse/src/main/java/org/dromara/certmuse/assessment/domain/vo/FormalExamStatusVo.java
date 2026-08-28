package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Observable asynchronous result-pipeline state. */
public record FormalExamStatusVo(String sessionId, String status, String action,
                                 List<StageVo> stages, FailureVo failure) {
    public record StageVo(String code, String state) { }
    public record FailureVo(String message, String traceId, boolean retryAvailable) { }
}
