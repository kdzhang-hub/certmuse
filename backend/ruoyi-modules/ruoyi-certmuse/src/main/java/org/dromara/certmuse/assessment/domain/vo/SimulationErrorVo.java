package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Stable U13 error payload. */
public record SimulationErrorVo(
    String errorCode,
    boolean retryable,
    String traceId,
    List<FieldErrorVo> fieldErrors,
    DetailsVo details
) {
    public record FieldErrorVo(String field, String code, String message) {
    }

    public record DetailsVo(
        String currentRevisionId,
        String sessionId,
        String collectionId,
        String answerPath
    ) {
    }
}
