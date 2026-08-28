package org.dromara.certmuse.catalog.support;

import java.util.List;
import org.dromara.certmuse.catalog.domain.vo.QualificationVersionBlockerVo;
import org.dromara.certmuse.catalog.domain.vo.QualificationVersionErrorVo;
import org.dromara.certmuse.catalog.domain.vo.QualificationVersionFieldErrorVo;
import org.dromara.certmuse.shared.web.CertMuseApiException;

/** Expected M01 business failure. */
public class QualificationVersionException extends CertMuseApiException {
    private final QualificationVersionErrorVo data;

    public QualificationVersionException(int status, String errorCode, String message) {
        this(status, errorCode, message, List.of(), List.of(), null);
    }

    public QualificationVersionException(int status, String errorCode, String message, Throwable cause) {
        this(status, errorCode, message, List.of(), List.of(), null, cause);
    }

    public QualificationVersionException(int status, String errorCode, String message,
                                         List<QualificationVersionFieldErrorVo> fieldErrors,
                                         List<QualificationVersionBlockerVo> blockers, String traceId) {
        this(status, errorCode, message, fieldErrors, blockers, traceId, null);
    }

    public QualificationVersionException(int status, String errorCode, String message,
                                         List<QualificationVersionFieldErrorVo> fieldErrors,
                                         List<QualificationVersionBlockerVo> blockers, String traceId,
                                         Throwable cause) {
        super(status, errorCode, message, false, traceId, List.of(), null, cause);
        this.data = new QualificationVersionErrorVo(errorCode, fieldErrors, blockers, traceId);
    }

    public QualificationVersionErrorVo data() { return data; }
}
