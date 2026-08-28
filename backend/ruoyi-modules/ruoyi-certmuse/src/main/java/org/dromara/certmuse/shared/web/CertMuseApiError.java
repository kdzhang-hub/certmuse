package org.dromara.certmuse.shared.web;

import java.util.List;

/**
 * Default error payload for CertMuse endpoints without a domain-specific error VO.
 */
public record CertMuseApiError(
    String errorCode,
    boolean retryable,
    String traceId,
    List<ApiFieldError> fieldErrors,
    Object details
) {
}
