package org.dromara.certmuse.shared.web;

import java.util.UUID;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.springframework.http.ResponseEntity;

/**
 * Builds aligned HTTP and {@link R} failure responses.
 */
@Slf4j
public final class CertMuseErrorResponses {
    private CertMuseErrorResponses() {
    }

    public static ResponseEntity<R<CertMuseApiError>> fail(CertMuseApiException exception) {
        return fail(exception, traceId -> new CertMuseApiError(
            exception.errorCode(), exception.retryable(), traceId,
            exception.apiFieldErrors(), exception.details()));
    }

    /**
     * Builds a domain-specific failure payload after resolving the boundary trace id.
     */
    public static <T> ResponseEntity<R<T>> fail(CertMuseApiException exception,
                                                 Function<String, T> dataFactory) {
        String traceId = resolveTraceId(exception);
        if (exception.status() >= 500) {
            log.error("CertMuse API failure, errorCode={}, traceId={}",
                exception.errorCode(), traceId, exception);
        }
        return fail(exception.status(), exception.getMessage(), dataFactory.apply(traceId));
    }

    public static <T> ResponseEntity<R<T>> fail(int status, String message, T data) {
        R<T> response = new R<>();
        response.setCode(status);
        response.setMsg(message);
        response.setData(data);
        return ResponseEntity.status(status).body(response);
    }

    /** Builds a status-aligned response without a business error payload. */
    public static ResponseEntity<R<Void>> fail(int status, String message) {
        return fail(status, message, null);
    }

    private static String resolveTraceId(CertMuseApiException exception) {
        if (exception.traceId() != null && !exception.traceId().isBlank()) {
            return exception.traceId();
        }
        return exception.status() >= 500 ? UUID.randomUUID().toString() : null;
    }
}
