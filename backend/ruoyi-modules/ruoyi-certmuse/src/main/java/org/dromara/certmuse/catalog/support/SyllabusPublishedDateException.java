package org.dromara.certmuse.catalog.support;

import java.util.List;

import org.dromara.certmuse.shared.web.ApiFieldError;
import org.dromara.certmuse.shared.web.CertMuseApiException;

/** Expected failure for the syllabus published-date update contract. */
public class SyllabusPublishedDateException extends CertMuseApiException {
    public SyllabusPublishedDateException(int status, String errorCode, String message) {
        this(status, errorCode, message, false, List.of(), null, null);
    }

    public SyllabusPublishedDateException(int status, String errorCode, String message, boolean retryable,
                                          List<ApiFieldError> fieldErrors, String traceId, Throwable cause) {
        super(status, errorCode, message, retryable, traceId, fieldErrors, null, cause);
    }
}
