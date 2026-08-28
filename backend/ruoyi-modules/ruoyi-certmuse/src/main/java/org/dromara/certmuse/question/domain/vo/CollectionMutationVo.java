package org.dromara.certmuse.question.domain.vo;

/**
 * 题集写操作的结果。
 */
public record CollectionMutationVo(
    String collectionId, String collectionCode, String revisionId,
    /** 对外展示的业务修订号，例如 V1、V2 对应的数字。 */ int revisionNo,
    String status,
    /** 草稿内容的乐观锁版本，不等同于 {@code revisionNo}。 */ String rowVersion,
    boolean createdRevision,
    /** 创建修订时实际复制的来源修订 ID。 */ String sourceRevisionId
) { }
