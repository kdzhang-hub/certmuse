package org.dromara.certmuse.catalog.domain;

import java.time.OffsetDateTime;

/** Internal projection for a textbook original-PDF attachment. */
public record TextbookPdfRow(Long documentId, String title, String status, String objectKey, String fileName,
                             Long fileSize, String fileHash, Long uploadedBy, OffsetDateTime uploadedTime) {
}
