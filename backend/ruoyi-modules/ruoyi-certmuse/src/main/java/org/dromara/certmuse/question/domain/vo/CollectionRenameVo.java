package org.dromara.certmuse.question.domain.vo;

/**
 * 题集稳定名称修改结果。
 *
 * @param collectionId 题集稳定标识
 * @param collectionName 修改后的稳定名称
 */
public record CollectionRenameVo(String collectionId, String collectionName) {
}
