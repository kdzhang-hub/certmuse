package org.dromara.certmuse.question.domain.vo;

import java.time.OffsetDateTime;

public record QuestionSaveResultVo(
    String questionId, String revisionId, int revisionNo, String rowVersion,
    String status, boolean createdNewRevision, OffsetDateTime updatedTime
) {
}
