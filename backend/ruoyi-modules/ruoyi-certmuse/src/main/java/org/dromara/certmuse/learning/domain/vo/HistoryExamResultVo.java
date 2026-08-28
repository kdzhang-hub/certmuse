package org.dromara.certmuse.learning.domain.vo;

import java.math.BigDecimal;

/** Report availability and score summary for one exam. */
public record HistoryExamResultVo(String reportStatus, String score, String maxScore, BigDecimal scoreRate) {}
