package org.dromara.certmuse.assessment.support;

import java.util.List;
import org.dromara.certmuse.assessment.domain.vo.PastPaperErrorVo;
import org.dromara.certmuse.shared.web.ApiFieldError;
import org.dromara.certmuse.shared.web.CertMuseApiException;

/** Expected U15 failure with a redacted, stable machine error code. */
public class PastPaperException extends CertMuseApiException {
    public PastPaperException(int status, String code, String message, boolean retryable) {
        this(status, code, message, retryable, List.of(), null);
    }
    public PastPaperException(int status, String code, String message, boolean retryable,
                              List<PastPaperErrorVo.FieldErrorVo> fields, Throwable cause) {
        super(status, code, message, retryable, null,
            fields.stream().map(field -> new ApiFieldError(field.field(), field.code(), field.message())).toList(), null, cause);
    }
}
