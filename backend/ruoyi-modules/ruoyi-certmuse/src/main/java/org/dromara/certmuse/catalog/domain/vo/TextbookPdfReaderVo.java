package org.dromara.certmuse.catalog.domain.vo;

import java.time.OffsetDateTime;

/** Short-lived reader URL for an already authorized textbook PDF. */
public record TextbookPdfReaderVo(String textbookId, String title, String fileName, String readUrl,
                                  OffsetDateTime expiresAt) {
}
