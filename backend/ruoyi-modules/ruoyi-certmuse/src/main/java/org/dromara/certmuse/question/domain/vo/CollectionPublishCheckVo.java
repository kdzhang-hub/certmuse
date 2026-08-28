package org.dromara.certmuse.question.domain.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 题集发布检查结果。
 */
public record CollectionPublishCheckVo(
    boolean passed, List<IssueVo> blockingIssues, List<IssueVo> warnings, SummaryVo summary
) {
    public record IssueVo(String code, String message, Integer itemOrder) { }
    public record SummaryVo(int questionCount, BigDecimal totalReportScore, int subjectCount) { }
}
