package org.dromara.certmuse.learning.support;

import java.util.List;
import org.dromara.certmuse.learning.domain.vo.HistoryErrorVo;
import org.dromara.certmuse.shared.web.ApiFieldError;
import org.dromara.certmuse.shared.web.CertMuseApiException;

/** Expected and redacted learning-history failure. */
public class LearningHistoryException extends CertMuseApiException {
    public LearningHistoryException(int status, String errorCode, String message) {
        this(status, errorCode, message, false, List.of(), null, null);
    }

    public LearningHistoryException(int status, String errorCode, String message, boolean retryable,
                                    List<HistoryErrorVo.FieldErrorVo> fields, Object details, Throwable cause) {
        super(status, errorCode, message, retryable, null,
            fields.stream().map(x -> new ApiFieldError(x.field(), x.code(), x.message())).toList(),
            details, cause);
    }
}
