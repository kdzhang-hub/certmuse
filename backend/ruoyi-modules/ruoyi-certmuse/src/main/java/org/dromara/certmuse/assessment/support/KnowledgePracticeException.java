package org.dromara.certmuse.assessment.support;

import java.util.List;
import org.dromara.certmuse.assessment.domain.vo.KnowledgePracticeErrorVo;
import org.dromara.certmuse.shared.web.ApiFieldError;
import org.dromara.certmuse.shared.web.CertMuseApiException;

/** Expected, redacted failure raised by the U08/U09 knowledge-practice use cases. */
public class KnowledgePracticeException extends CertMuseApiException {
    public KnowledgePracticeException(int status, String code, String message, boolean retryable) {
        this(status, code, message, retryable, List.of(), null, null);
    }

    public KnowledgePracticeException(int status, String code, String message, boolean retryable,
                                      List<KnowledgePracticeErrorVo.FieldErrorVo> fields,
                                      KnowledgePracticeErrorVo.DetailsVo details, Throwable cause) {
        super(status, code, message, retryable, null,
            fields.stream().map(field -> new ApiFieldError(field.field(), field.code(), field.message())).toList(),
            details, cause);
    }
}
