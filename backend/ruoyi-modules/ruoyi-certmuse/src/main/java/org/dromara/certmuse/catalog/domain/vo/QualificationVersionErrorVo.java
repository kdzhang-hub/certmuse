package org.dromara.certmuse.catalog.domain.vo;

import java.util.List;

/** Structured M01 error payload. */
public record QualificationVersionErrorVo(String errorCode, List<QualificationVersionFieldErrorVo> fieldErrors,
                                          List<QualificationVersionBlockerVo> blockers, String traceId) {
}
