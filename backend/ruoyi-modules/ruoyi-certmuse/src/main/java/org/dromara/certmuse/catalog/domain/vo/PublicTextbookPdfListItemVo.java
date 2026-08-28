package org.dromara.certmuse.catalog.domain.vo;

import java.time.OffsetDateTime;

/** Published textbook PDF metadata safe for anonymous browsing. */
public record PublicTextbookPdfListItemVo(String id, String title, String edition, String syllabusVersionName,
                                          String fileName, long fileSize, OffsetDateTime uploadedTime) {
}
