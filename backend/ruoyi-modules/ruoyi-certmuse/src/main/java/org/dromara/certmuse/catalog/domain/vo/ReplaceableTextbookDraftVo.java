package org.dromara.certmuse.catalog.domain.vo;

/** Draft textbook that can be replaced by a manual import. */
public record ReplaceableTextbookDraftVo(
    String id,
    String title,
    String edition,
    String syllabusVersionId
) {
}
