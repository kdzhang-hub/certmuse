package org.dromara.certmuse.assessment.support;

import lombok.Getter;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticErrorVo;
import org.dromara.certmuse.shared.web.ApiFieldError;
import org.dromara.certmuse.shared.web.CertMuseApiException;

import java.util.List;

/** Deliberately redacted business exception for the learner diagnostic API. */
@Getter
public class DiagnosticException extends CertMuseApiException {
    private final DiagnosticErrorVo data;

    public DiagnosticException(int status, String code, String message) {
        this(status, code, message, false, null, List.of());
    }

    public DiagnosticException(int status, String code, String message, boolean retryable, String nextAction) {
        this(status, code, message, retryable, nextAction, List.of());
    }

    public DiagnosticException(int status, String code, String message, boolean retryable, String nextAction,
                               List<DiagnosticErrorVo.FieldErrorVo> fields) {
        this(status, code, message, retryable, nextAction, fields, null);
    }

    public DiagnosticException(int status, String code, String message, boolean retryable, String nextAction,
                               Throwable cause) {
        this(status, code, message, retryable, nextAction, List.of(), cause);
    }

    public DiagnosticException(int status, String code, String message, boolean retryable, String nextAction,
                               List<DiagnosticErrorVo.FieldErrorVo> fields, Throwable cause) {
        super(status, code, message, retryable, null,
            fields.stream().map(field -> new ApiFieldError(field.field(), field.code(), field.message())).toList(),
            null, cause);
        this.data = new DiagnosticErrorVo(code, retryable, null, nextAction,
            fields == null ? List.of() : List.copyOf(fields));
    }
}
