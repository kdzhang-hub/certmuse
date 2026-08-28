package org.dromara.certmuse.catalog.domain;

/** Textbook document projection used by import workflows. */
public record TextbookImportDocument(
    long id,
    Long syllabusVersionId,
    String title,
    String edition,
    String status,
    String delFlag,
    Long createBy,
    Long createDept,
    long certificationId
) {
    public TextbookImportDocument(
        long id, long syllabusVersionId, String title, String edition, String status,
        String delFlag, Long createBy, Long createDept
    ) {
        this(id, syllabusVersionId, title, edition, status, delFlag, createBy, createDept, 0L);
    }
}
