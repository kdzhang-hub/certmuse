package org.dromara.certmuse.catalog.domain.vo;

import java.util.List;

/**
 * Structured import error response data.
 */
public record ImportErrorVo(
    String errorCode,
    boolean retryable,
    String traceId,
    List<ImportFieldErrorVo> fieldErrors
) {
}
