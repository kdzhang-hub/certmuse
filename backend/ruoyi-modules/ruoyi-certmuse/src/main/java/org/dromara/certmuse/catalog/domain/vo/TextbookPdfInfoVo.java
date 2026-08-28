package org.dromara.certmuse.catalog.domain.vo;

import java.time.OffsetDateTime;

/** Safe original-PDF metadata shown to administrators. */
public record TextbookPdfInfoVo(boolean available, String fileName, Long fileSize, String fileHash,
                                OffsetDateTime uploadedTime) {
}
