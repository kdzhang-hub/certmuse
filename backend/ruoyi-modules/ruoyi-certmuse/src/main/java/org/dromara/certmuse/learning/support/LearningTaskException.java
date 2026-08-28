package org.dromara.certmuse.learning.support;

import java.util.List;
import org.dromara.certmuse.learning.domain.vo.LearningTaskErrorVo;
import org.dromara.certmuse.shared.web.ApiFieldError;
import org.dromara.certmuse.shared.web.CertMuseApiException;

/** Expected U10 task-pool failure. */
public class LearningTaskException extends CertMuseApiException {
    public LearningTaskException(int status, String errorCode, String message, boolean retryable) {
        this(status, errorCode, message, retryable, List.of(), null, null);
    }

    public LearningTaskException(int status, String errorCode, String message, boolean retryable,
                                 List<LearningTaskErrorVo.FieldErrorVo> fieldErrors, Object details, Throwable cause) {
        super(status, errorCode, message, retryable, null,
            fieldErrors.stream().map(error -> new ApiFieldError(error.field(), error.code(), error.message())).toList(),
            details, cause);
    }
}
