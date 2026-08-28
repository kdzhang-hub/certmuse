package org.dromara.certmuse.learning.domain.vo;

import java.util.List;

/** Machine-readable error data returned by U03 learning-goal endpoints. */
public record ContractErrorVo(
    String errorCode,
    boolean retryable,
    String traceId,
    List<FieldErrorVo> fieldErrors
) {
    /** A field-level validation error using the request JSON property name. */
    public record FieldErrorVo(String field, String code, String message) {
    }
}
