package org.dromara.certmuse.assessment.support;

import java.util.List;
import org.dromara.certmuse.assessment.domain.vo.PracticeAiErrorVo;
import org.dromara.certmuse.shared.web.ApiFieldError;
import org.dromara.certmuse.shared.web.CertMuseApiException;

/** Expected redacted learner AI chat failure. */
public class PracticeAiException extends CertMuseApiException {
    public PracticeAiException(int status, String code, String message, boolean retryable) {
        this(status, code, message, retryable, List.of(), null, null);
    }

    public PracticeAiException(int status, String code, String message, boolean retryable,
                               List<PracticeAiErrorVo.FieldErrorVo> fields,
                               PracticeAiErrorVo.DetailsVo details, Throwable cause) {
        super(status, code, message, retryable, null,
            fields.stream().map(field -> new ApiFieldError(field.field(), field.code(), field.message())).toList(),
            details, cause);
    }
}
