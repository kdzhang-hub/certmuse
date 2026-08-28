package org.dromara.certmuse.assessment.support;

import java.util.List;
import org.dromara.certmuse.assessment.domain.vo.KnowledgePracticeErrorVo;
import org.dromara.certmuse.shared.web.ApiFieldError;
import org.dromara.certmuse.shared.web.CertMuseApiException;

/** Expected U11 request, ownership and state-machine failure. */
public class DailyTaskException extends CertMuseApiException {
    public DailyTaskException(int status, String code, String message, boolean retryable) {
        this(status, code, message, retryable, List.of(), null);
    }

    public DailyTaskException(int status, String code, String message, boolean retryable,
                              List<KnowledgePracticeErrorVo.FieldErrorVo> fields, Throwable cause) {
        super(status, code, message, retryable, null,
            fields.stream().map(f -> new ApiFieldError(f.field(), f.code(), f.message())).toList(), null, cause);
    }
}
