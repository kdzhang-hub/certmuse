package org.dromara.certmuse.assessment.support;

import java.util.List;
import org.dromara.certmuse.assessment.domain.vo.SimulationErrorVo;
import org.dromara.certmuse.shared.web.ApiFieldError;
import org.dromara.certmuse.shared.web.CertMuseApiException;

/** Expected, redacted failure raised by the U13 simulation use cases. */
public class SimulationException extends CertMuseApiException {
    public SimulationException(int status, String code, String message, boolean retryable) {
        this(status, code, message, retryable, List.of(), null, null);
    }

    public SimulationException(int status, String code, String message, boolean retryable,
                               List<SimulationErrorVo.FieldErrorVo> fields,
                               SimulationErrorVo.DetailsVo details, Throwable cause) {
        super(status, code, message, retryable, null,
            fields.stream().map(field -> new ApiFieldError(field.field(), field.code(), field.message())).toList(),
            details, cause);
    }
}
