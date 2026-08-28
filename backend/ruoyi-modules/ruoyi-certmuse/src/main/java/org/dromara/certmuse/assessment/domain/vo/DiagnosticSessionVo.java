package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

public record DiagnosticSessionVo(String sessionId, String status, String nextAction, long sessionVersion,
                                  int currentQuestionOrder, int answeredCount, int unansweredCount,
                                  List<NavigationVo> navigation, int estimatedDurationSeconds,
                                  long effectiveElapsedSeconds, String serverTime) {
    public record NavigationVo(int questionOrder, String state) { }
}
