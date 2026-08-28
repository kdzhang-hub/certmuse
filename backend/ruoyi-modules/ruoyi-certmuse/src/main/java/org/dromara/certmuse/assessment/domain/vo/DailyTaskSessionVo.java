package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Daily-task answering summary and navigation. */
public record DailyTaskSessionVo(String sessionId, String sessionStatus, String taskTitle, int totalCount, int submittedCount,
                                 List<NavigationItemVo> navigation, String nextAction) {
    public record NavigationItemVo(int questionOrder, String state) {}
}
