package org.dromara.certmuse.question.domain.vo;

import java.util.List;

/**
 * 题集业务异常的机器可读补充信息。
 */
public record CollectionErrorVo(
    String errorCode, String currentRevisionId, String currentRowVersion,
    String workingRevisionId, String traceId, List<CollectionPublishCheckVo.IssueVo> blockingIssues
) { }
