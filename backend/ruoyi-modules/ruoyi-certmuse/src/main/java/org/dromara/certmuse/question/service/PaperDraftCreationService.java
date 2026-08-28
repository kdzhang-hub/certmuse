package org.dromara.certmuse.question.service;

import java.util.List;

/**
 * Question-domain contract for creating the draft collection produced by a paper import.
 */
public interface PaperDraftCreationService {

    PaperDraftCreationResult create(PaperDraftCreationCommand command);

    record PaperDraftCreationCommand(
        long certificationId,
        long syllabusVersionId,
        String collectionName,
        String collectionType,
        int durationMinutes,
        Integer examYear,
        Integer examMonth,
        String paperTypeCode,
        String paperTypeName,
        Long visibleUserId,
        List<Long> questionRevisionIds
    ) {
        /** Compatibility constructor for non-past-paper imports. */
        public PaperDraftCreationCommand(
            long certificationId,
            long syllabusVersionId,
            String collectionName,
            String collectionType,
            int durationMinutes,
            Long visibleUserId,
            List<Long> questionRevisionIds
        ) {
            this(certificationId, syllabusVersionId, collectionName, collectionType, durationMinutes,
                null, null, null, null, visibleUserId, questionRevisionIds);
        }
    }

    record PaperDraftCreationResult(long collectionId, long collectionRevisionId) {
    }
}
