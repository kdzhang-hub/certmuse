package org.dromara.certmuse.question.domain;

import lombok.Data;

/**
 * 题集写操作幂等记录的持久化投影。
 */
@Data
public class CollectionIdempotencyRow {
    private Long id;
    private String payloadHash;
    private String status;
    private Long resourceId;
    private String responseBody;
}
