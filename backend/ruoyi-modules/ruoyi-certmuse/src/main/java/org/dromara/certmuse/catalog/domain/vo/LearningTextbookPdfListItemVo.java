package org.dromara.certmuse.catalog.domain.vo;

import java.time.OffsetDateTime;

/** Published original textbook PDF visible to one learner. */
public record LearningTextbookPdfListItemVo(String id, String title, String edition, String syllabusVersionName,
                                            String fileName, long fileSize, OffsetDateTime uploadedTime) {
}
