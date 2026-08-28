package org.dromara.certmuse.catalog.domain.vo;

import java.time.OffsetDateTime;

/**
 * Import validation issue response.
 */
public record ImportIssueVo(
    int lineNo,
    String sourceKey,
    String fieldPath,
    String severity,
    String issueCode,
    String message,
    OffsetDateTime createTime
) {
}
