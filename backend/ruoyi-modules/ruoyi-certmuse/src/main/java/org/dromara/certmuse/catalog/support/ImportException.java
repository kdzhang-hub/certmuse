package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.catalog.domain.vo.ImportFieldErrorVo;
import org.dromara.certmuse.shared.web.CertMuseApiException;

import java.util.List;

/**
 * Import API exception carrying a stable machine error code.
 */
public class ImportException extends CertMuseApiException {

    private final List<ImportFieldErrorVo> fieldErrors;

    public ImportException(int code, String errorCode, String message) {
        this(code, errorCode, message, false, null, List.of());
    }

    public ImportException(int code, String errorCode, String message, Throwable cause) {
        this(code, errorCode, message, false, null, List.of(), cause);
    }

    public ImportException(
        int code,
        String errorCode,
        String message,
        boolean retryable,
        String traceId,
        List<ImportFieldErrorVo> fieldErrors
    ) {
        this(code, errorCode, message, retryable, traceId, fieldErrors, null);
    }

    public ImportException(
        int code,
        String errorCode,
        String message,
        boolean retryable,
        String traceId,
        List<ImportFieldErrorVo> fieldErrors,
        Throwable cause
    ) {
        super(code, errorCode, message, retryable, traceId, List.of(), null, cause);
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public int code() {
        return status();
    }

    public List<ImportFieldErrorVo> fieldErrors() {
        return fieldErrors;
    }
}
