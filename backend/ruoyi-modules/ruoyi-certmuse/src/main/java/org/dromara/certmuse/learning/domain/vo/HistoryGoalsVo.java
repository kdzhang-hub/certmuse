package org.dromara.certmuse.learning.domain.vo;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Goals that own at least one visible learning-history fact. */
public record HistoryGoalsVo(List<RowVo> rows) {
    public record RowVo(String goalId, String certificationName, String syllabusVersionName,
                        LocalDate targetExamDate, String status, boolean isCurrent,
                        OffsetDateTime lastRecordAt) {}
}
