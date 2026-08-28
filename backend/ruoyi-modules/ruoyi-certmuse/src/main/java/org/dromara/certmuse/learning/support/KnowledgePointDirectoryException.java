package org.dromara.certmuse.learning.support;

import java.util.List;
import org.dromara.certmuse.learning.domain.vo.KnowledgePointDirectoryErrorVo;
import org.dromara.certmuse.shared.web.ApiFieldError;
import org.dromara.certmuse.shared.web.CertMuseApiException;

/** Expected U07 failure with a redacted, versioned response body. */
public class KnowledgePointDirectoryException extends CertMuseApiException {
    private final KnowledgePointDirectoryErrorVo data;

    public KnowledgePointDirectoryException(int status, String errorCode, String message, boolean retryable,
                                            List<KnowledgePointDirectoryErrorVo.FieldErrorVo> fieldErrors,
                                            Throwable cause) {
        super(status, errorCode, message, retryable, null,
            fieldErrors == null ? List.of() : fieldErrors.stream()
                .map(error -> new ApiFieldError(error.field(), error.code(), error.message())).toList(),
            null, cause);
        this.data = new KnowledgePointDirectoryErrorVo(errorCode, retryable, null, fieldErrors);
    }

    public KnowledgePointDirectoryErrorVo data() {
        return data;
    }
}
