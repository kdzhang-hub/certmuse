package org.dromara.certmuse.learning.support;

import java.util.List;
import org.dromara.certmuse.learning.domain.vo.ContractErrorVo;
import org.dromara.certmuse.learning.domain.vo.GoalSwitchErrorVo;
import org.dromara.certmuse.shared.web.CertMuseApiException;

/** Expected failure defined by the U12 learning-goal switch contract. */
public class GoalSwitchException extends CertMuseApiException {
    private final GoalSwitchErrorVo data;

    public GoalSwitchException(int status, String errorCode, String message, boolean retryable,
                               List<ContractErrorVo.FieldErrorVo> fieldErrors,
                               GoalSwitchErrorVo.DetailsVo details, Throwable cause) {
        super(status, errorCode, message, retryable, null, List.of(), details, cause);
        this.data = new GoalSwitchErrorVo(errorCode, retryable, null,
            fieldErrors == null ? List.of() : List.copyOf(fieldErrors), details);
    }

    public GoalSwitchErrorVo data() {
        return data;
    }
}
