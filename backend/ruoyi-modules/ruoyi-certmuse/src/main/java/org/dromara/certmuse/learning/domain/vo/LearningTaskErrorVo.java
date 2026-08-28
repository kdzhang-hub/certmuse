package org.dromara.certmuse.learning.domain.vo;

import java.util.List;

/** Stable U10 expected-error payload. */
public record LearningTaskErrorVo(
    String errorCode,
    boolean retryable,
    String traceId,
    List<FieldErrorVo> fieldErrors,
    Object details
) {
    public record FieldErrorVo(String field, String code, String message) {
    }
}
