package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Stable U15 expected-error envelope. */
public record PastPaperErrorVo(String errorCode, boolean retryable, String traceId,
                               List<FieldErrorVo> fieldErrors, Object details) {
    public record FieldErrorVo(String field, String code, String message) {}
}
