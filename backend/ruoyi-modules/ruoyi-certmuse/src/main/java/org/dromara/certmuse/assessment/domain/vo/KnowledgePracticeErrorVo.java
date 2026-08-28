package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Frozen U08 business error payload. */
public record KnowledgePracticeErrorVo(
    String errorCode, boolean retryable, String traceId,
    List<FieldErrorVo> fieldErrors, DetailsVo details
) {
    public record FieldErrorVo(String field, String code, String message) {}
    public record DetailsVo(String answerPath) {}
}
