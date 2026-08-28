package org.dromara.certmuse.catalog.domain;

/** Paper-specific batch metadata and final collection references. */
public record PaperImportRow(
    long batchId,
    long certificationId,
    String collectionName,
    String collectionType,
    int durationMinutes,
    Integer examYear,
    Integer examMonth,
    String paperTypeCode,
    String paperTypeName,
    Long collectionId,
    Long collectionRevisionId
) {
    /** Compatibility constructor for paper imports created before past-paper metadata was captured. */
    public PaperImportRow(
        long batchId,
        long certificationId,
        String collectionName,
        String collectionType,
        int durationMinutes,
        Long collectionId,
        Long collectionRevisionId
    ) {
        this(batchId, certificationId, collectionName, collectionType, durationMinutes,
            null, null, null, null, collectionId, collectionRevisionId);
    }
}
