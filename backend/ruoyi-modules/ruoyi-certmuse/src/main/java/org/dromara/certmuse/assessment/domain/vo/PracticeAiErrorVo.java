package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Stable learner AI chat failure envelope. */
public record PracticeAiErrorVo(
    String errorCode,
    boolean retryable,
    String traceId,
    List<FieldErrorVo> fieldErrors,
    DetailsVo details
) {
    public record FieldErrorVo(String field, String code, String message) {
    }

    public record DetailsVo(
        String conversationId,
        String assistantMessageId,
        Integer retryAfterSeconds,
        String limitType
    ) {
    }
}
