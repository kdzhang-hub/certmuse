package org.dromara.certmuse.learning.domain.vo;

import java.util.List;

/** Contract-stable business error payload for U07 directory lookup. */
public record KnowledgePointDirectoryErrorVo(
    String errorCode,
    boolean retryable,
    String traceId,
    List<FieldErrorVo> fieldErrors
) {
    public KnowledgePointDirectoryErrorVo {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    /** One client-correctable query field failure. */
    public record FieldErrorVo(String field, String code, String message) {
    }
}
