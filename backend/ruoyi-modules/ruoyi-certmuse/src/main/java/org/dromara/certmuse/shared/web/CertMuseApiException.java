package org.dromara.certmuse.shared.web;

import java.util.List;

/**
 * Common metadata carried by an expected CertMuse API failure.
 */
public class CertMuseApiException extends RuntimeException {
    private final int status;
    private final String errorCode;
    private final boolean retryable;
    private final String traceId;
    private final List<ApiFieldError> fieldErrors;
    private final Object details;

    public CertMuseApiException(int status, String errorCode, String message) {
        this(status, errorCode, message, false, null, List.of(), null, null);
    }

    public CertMuseApiException(int status, String errorCode, String message, boolean retryable,
                                String traceId, List<ApiFieldError> fieldErrors, Object details,
                                Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.traceId = traceId;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
        this.details = details;
    }

    public int status() { return status; }
    public String errorCode() { return errorCode; }
    public boolean retryable() { return retryable; }
    public String traceId() { return traceId; }
    public List<ApiFieldError> apiFieldErrors() { return fieldErrors; }
    public Object details() { return details; }

    /** Compatibility accessor for existing domain callers. */
    public int getStatus() { return status; }
    /** Compatibility accessor for existing domain callers. */
    public String getErrorCode() { return errorCode; }
    /** Compatibility accessor for existing domain callers. */
    public boolean isRetryable() { return retryable; }
    /** Compatibility accessor for existing domain callers. */
    public String getTraceId() { return traceId; }
}
