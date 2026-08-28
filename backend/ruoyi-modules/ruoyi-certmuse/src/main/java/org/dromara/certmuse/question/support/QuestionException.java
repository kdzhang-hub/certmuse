package org.dromara.certmuse.question.support;

import lombok.Getter;
import org.dromara.certmuse.question.domain.vo.QuestionErrorVo;
import org.dromara.certmuse.shared.web.CertMuseApiException;

import java.util.List;

@Getter
public class QuestionException extends CertMuseApiException {
    private final QuestionErrorVo data;

    public QuestionException(int status, String errorCode, String message) {
        this(status, errorCode, message, null, null, null);
    }

    public QuestionException(int status, String errorCode, String message, Throwable cause) {
        this(status, errorCode, message, null, null, null, List.of(), cause);
    }

    public QuestionException(int status, String errorCode, String message,
                             String currentRevisionId, String currentRowVersion, String workingRevisionId) {
        this(status, errorCode, message, currentRevisionId, currentRowVersion, workingRevisionId, List.of());
    }

    public QuestionException(int status, String errorCode, String message,
                             List<QuestionErrorVo.BlockingIssueVo> blockingIssues) {
        this(status, errorCode, message, null, null, null, blockingIssues);
    }

    private QuestionException(int status, String errorCode, String message,
                              String currentRevisionId, String currentRowVersion, String workingRevisionId,
                              List<QuestionErrorVo.BlockingIssueVo> blockingIssues) {
        this(status, errorCode, message, currentRevisionId, currentRowVersion, workingRevisionId,
            blockingIssues, null);
    }

    private QuestionException(int status, String errorCode, String message,
                              String currentRevisionId, String currentRowVersion, String workingRevisionId,
                              List<QuestionErrorVo.BlockingIssueVo> blockingIssues, Throwable cause) {
        super(status, errorCode, message, false, null, List.of(), null, cause);
        this.data = new QuestionErrorVo(errorCode, currentRevisionId, currentRowVersion,
            workingRevisionId, null, blockingIssues == null ? List.of() : List.copyOf(blockingIssues));
    }
}
