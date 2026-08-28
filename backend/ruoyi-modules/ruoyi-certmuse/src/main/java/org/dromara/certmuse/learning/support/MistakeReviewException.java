package org.dromara.certmuse.learning.support;

import java.util.List;
import org.dromara.certmuse.learning.domain.vo.MistakeErrorVo;
import org.dromara.certmuse.shared.web.ApiFieldError;
import org.dromara.certmuse.shared.web.CertMuseApiException;

/** Expected and redacted mistake-review failure. */
public class MistakeReviewException extends CertMuseApiException {
    public MistakeReviewException(int status, String errorCode, String message) {
        this(status, errorCode, message, false, List.of(), null, null);
    }
    public MistakeReviewException(int status, String errorCode, String message, boolean retryable,
                                  List<MistakeErrorVo.FieldErrorVo> fields, MistakeErrorVo.DetailsVo details,
                                  Throwable cause) {
        super(status, errorCode, message, retryable, null,
            fields.stream().map(x -> new ApiFieldError(x.field(), x.code(), x.message())).toList(), details, cause);
    }
}
