package org.dromara.certmuse.learning.support;

import java.util.List;
import org.dromara.certmuse.learning.domain.vo.ContractErrorVo;
import org.dromara.certmuse.shared.web.CertMuseApiException;

/** Expected business failure for the learner first-goal flow. */
public class LearningGoalException extends CertMuseApiException {
    private final ContractErrorVo data;

    public LearningGoalException(int status, String errorCode, String message) {
        this(status, errorCode, message, false, null, List.of());
    }

    public LearningGoalException(int status, String errorCode, String message, Throwable cause) {
        this(status, errorCode, message, false, null, List.of(), cause);
    }

    public LearningGoalException(int status, String errorCode, String message, boolean retryable,
                                 String traceId, List<ContractErrorVo.FieldErrorVo> fieldErrors) {
        this(status, errorCode, message, retryable, traceId, fieldErrors, null);
    }

    public LearningGoalException(int status, String errorCode, String message, boolean retryable,
                                 String traceId, List<ContractErrorVo.FieldErrorVo> fieldErrors,
                                 Throwable cause) {
        super(status, errorCode, message, retryable, traceId, List.of(), null, cause);
        this.data = new ContractErrorVo(errorCode, retryable, traceId, fieldErrors == null ? List.of() : fieldErrors);
    }

    public ContractErrorVo data() {
        return data;
    }
}
