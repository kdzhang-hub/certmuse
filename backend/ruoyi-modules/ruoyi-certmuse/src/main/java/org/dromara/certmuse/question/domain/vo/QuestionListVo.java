package org.dromara.certmuse.question.domain.vo;

import java.time.OffsetDateTime;

public record QuestionListVo(
    String questionId, String revisionId, int revisionNo, String questionCode,
    String syllabusVersionId, String syllabusVersionName, String stemSummary,
    String examSubjectId, String examSubjectName, String questionType,
    String difficulty, String status, boolean hasReviewOpinion, OffsetDateTime updatedTime
) {
}
