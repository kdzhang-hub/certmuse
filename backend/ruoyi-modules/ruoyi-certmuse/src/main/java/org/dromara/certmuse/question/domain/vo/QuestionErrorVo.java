package org.dromara.certmuse.question.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public record QuestionErrorVo(
    String errorCode, String currentRevisionId, String currentRowVersion,
    String workingRevisionId, String traceId,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<BlockingIssueVo> blockingIssues
) {
    public QuestionErrorVo(String errorCode, String currentRevisionId, String currentRowVersion,
                           String workingRevisionId, String traceId) {
        this(errorCode, currentRevisionId, currentRowVersion, workingRevisionId, traceId, List.of());
    }

    /** 提交审核门禁的单项问题。 */
    public record BlockingIssueVo(String code, String fieldPath, String message) {
    }
}
