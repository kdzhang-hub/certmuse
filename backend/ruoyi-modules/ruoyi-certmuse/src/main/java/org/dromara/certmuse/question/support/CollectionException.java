package org.dromara.certmuse.question.support;

import lombok.Getter;
import org.dromara.certmuse.question.domain.vo.CollectionErrorVo;
import org.dromara.certmuse.question.domain.vo.CollectionPublishCheckVo;
import org.dromara.certmuse.shared.web.CertMuseApiException;

import java.util.List;

@Getter
public class CollectionException extends CertMuseApiException {
    private final CollectionErrorVo data;

    public CollectionException(int status, String code, String message) {
        this(status, code, message, null, null, null, null);
    }

    public CollectionException(int status, String code, String message, Throwable cause) {
        this(status, code, message, null, null, null, null, cause);
    }

    public CollectionException(int status, String code, String message,
                               String currentRevisionId, String currentRowVersion, String workingRevisionId) {
        this(status, code, message, currentRevisionId, currentRowVersion, workingRevisionId, null);
    }

    public CollectionException(int status, String code, String message,
                               List<CollectionPublishCheckVo.IssueVo> blockingIssues) {
        this(status, code, message, null, null, null, blockingIssues);
    }

    private CollectionException(int status, String code, String message,
                                String currentRevisionId, String currentRowVersion, String workingRevisionId,
                                List<CollectionPublishCheckVo.IssueVo> blockingIssues) {
        this(status, code, message, currentRevisionId, currentRowVersion, workingRevisionId,
            blockingIssues, null);
    }

    private CollectionException(int status, String code, String message,
                                String currentRevisionId, String currentRowVersion, String workingRevisionId,
                                List<CollectionPublishCheckVo.IssueVo> blockingIssues, Throwable cause) {
        super(status, code, message, false, null, List.of(), null, cause);
        this.data = new CollectionErrorVo(code, currentRevisionId, currentRowVersion,
            workingRevisionId, null, blockingIssues == null ? List.of() : List.copyOf(blockingIssues));
    }
}
