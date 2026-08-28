package org.dromara.certmuse.catalog.support;

import java.util.List;
import lombok.Getter;
import org.dromara.certmuse.catalog.domain.vo.TextbookErrorVo;
import org.dromara.certmuse.shared.web.CertMuseApiException;

@Getter
public class TextbookException extends CertMuseApiException {
    private final List<TextbookErrorVo.FieldErrorVo> fieldErrors;
    public TextbookException(int status, String errorCode, String message) { this(status, errorCode, message, false); }
    public TextbookException(int status, String errorCode, String message, Throwable cause) {
        this(status, errorCode, message, false, List.of(), cause);
    }
    public TextbookException(int status, String errorCode, String message, boolean retryable) {
        this(status, errorCode, message, retryable, List.of());
    }
    public TextbookException(int status, String errorCode, String message, boolean retryable,
                             List<TextbookErrorVo.FieldErrorVo> fieldErrors) {
        this(status, errorCode, message, retryable, fieldErrors, null);
    }
    public TextbookException(int status, String errorCode, String message, boolean retryable,
                             List<TextbookErrorVo.FieldErrorVo> fieldErrors, Throwable cause) {
        super(status, errorCode, message, retryable, null, List.of(), null, cause);
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }
}
