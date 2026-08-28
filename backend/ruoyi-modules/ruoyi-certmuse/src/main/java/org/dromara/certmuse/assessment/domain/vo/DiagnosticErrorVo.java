package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Redacted, contract-stable error body for diagnostic business failures. */
public record DiagnosticErrorVo(String errorCode, boolean retryable, String traceId,
                                String nextAction, List<FieldErrorVo> fieldErrors) {
    public record FieldErrorVo(String field, String code, String message) { }
}
