package org.dromara.certmuse.learning.domain.vo;

import java.util.List;

/** Options and defaults used by the first learning-goal form. */
public record LearningGoalOptionsVo(
    String serverTime,
    String timezone,
    List<CertificationOptionVo> certifications,
    List<ExamYearOptionVo> examYears,
    DailyMinutesRuleVo dailyMinutes,
    DefaultsVo defaults
) {
    public record CertificationOptionVo(String id, String code, String name) {
    }

    public record ExamYearOptionVo(int year, List<Integer> months, boolean selectable) {
    }

    public record DailyMinutesRuleVo(int min, int max, int defaultValue) {
    }

    public record DefaultsVo(String certificationId, int examYear, int examMonth) {
    }
}
