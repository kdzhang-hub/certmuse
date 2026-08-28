package org.dromara.certmuse.learning.domain.vo;

import java.util.List;

/** Stable V1 mistake-review failure envelope. */
public record MistakeErrorVo(String errorCode, boolean retryable, String traceId,
                             List<FieldErrorVo> fieldErrors, DetailsVo details) {
    public record FieldErrorVo(String field, String code, String message) {}
    public record DetailsVo(String sessionId, String questionId, String reason) {}
}
