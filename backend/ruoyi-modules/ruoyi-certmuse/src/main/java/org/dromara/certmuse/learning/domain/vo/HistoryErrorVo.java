package org.dromara.certmuse.learning.domain.vo;

import java.util.List;

/** Stable V1 learning-history failure envelope. */
public record HistoryErrorVo(String errorCode, boolean retryable, String traceId,
                             List<FieldErrorVo> fieldErrors, Object details) {
    public record FieldErrorVo(String field, String code, String message) {}
}
