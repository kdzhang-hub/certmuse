package org.dromara.certmuse.learning.domain.vo;

import java.util.List;

/** Paginated completed exam history. */
public record HistoryExamPageVo(List<HistoryExamRowVo> rows, long total) {}
