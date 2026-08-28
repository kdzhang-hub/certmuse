package org.dromara.certmuse.catalog.domain.vo;

import java.util.List;

public record TextbookErrorVo(String errorCode, boolean retryable, String traceId,
                              List<FieldErrorVo> fieldErrors) {
    public record FieldErrorVo(String field, String code, String message) {}
}
