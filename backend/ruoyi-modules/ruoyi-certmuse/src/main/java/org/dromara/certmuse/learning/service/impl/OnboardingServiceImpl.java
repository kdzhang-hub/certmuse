package org.dromara.certmuse.learning.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.learning.domain.OnboardingRow;
import org.dromara.certmuse.learning.domain.vo.OnboardingStatusVo;
import org.dromara.certmuse.learning.mapper.OnboardingMapper;
import org.dromara.certmuse.learning.service.OnboardingService;
import org.dromara.certmuse.learning.support.OnboardingException;
import org.springframework.stereotype.Service;

/** Calculates the deterministic learner onboarding state from owned learning records. */
@Service
@RequiredArgsConstructor
public class OnboardingServiceImpl implements OnboardingService {

    private final OnboardingMapper onboardingMapper;

    @Override
    public OnboardingStatusVo status(Long userId) {
        OnboardingRow row = onboardingMapper.selectStatus(userId);
        OnboardingStatusVo result = new OnboardingStatusVo();
        if (row == null || row.getGoalId() == null) {
            result.setGoalStatus("NONE");
            result.setDiagnosticStatus("NOT_STARTED");
            result.setNextAction("SET_GOAL");
            return result;
        }
        if (!"active".equals(row.getGoalStatus()) && !"paused".equals(row.getGoalStatus())) {
            throw invalidState();
        }
        result.setGoalStatus("active".equals(row.getGoalStatus()) ? "ACTIVE" : "PAUSED");
        result.setCurrentCertificationId(Long.toString(row.getCertificationId()));
        if ("paused".equals(row.getGoalStatus())) {
            result.setDiagnosticStatus("NOT_STARTED");
            result.setNextAction("CONTACT_SUPPORT");
            return result;
        }
        if (row.getSessionId() == null) {
            result.setDiagnosticStatus("NOT_STARTED");
            result.setNextAction("START_DIAGNOSTIC");
            return result;
        }
        result.setActiveSessionId(Long.toString(row.getSessionId()));
        switch (row.getSessionStatus()) {
            case "created", "in_progress" -> {
                result.setDiagnosticStatus("IN_PROGRESS");
                result.setNextAction("CONTINUE_DIAGNOSTIC");
            }
            case "submitted", "settling" -> {
                result.setDiagnosticStatus("PROCESSING");
                result.setNextAction("WAIT_PROCESSING");
            }
            case "invalid", "cancelled" -> {
                result.setDiagnosticStatus("FAILED");
                result.setNextAction("RETRY_DIAGNOSTIC");
            }
            case "completed" -> completed(result, row);
            default -> throw invalidState();
        }
        return result;
    }

    private void completed(OnboardingStatusVo result, OnboardingRow row) {
        if ("failed".equals(row.getReportStatus())) {
            result.setDiagnosticStatus("FAILED");
            result.setNextAction("RETRY_DIAGNOSTIC_RESULT");
        } else if ("generating".equals(row.getReportStatus())) {
            result.setDiagnosticStatus("PROCESSING");
            result.setNextAction("WAIT_PROCESSING");
        } else if ("available".equals(row.getReportStatus()) && row.getProfileCount() != null && row.getProfileCount() > 0) {
            result.setDiagnosticStatus("COMPLETED");
            result.setNextAction("VIEW_DIAGNOSTIC_REPORT");
        } else if ("available".equals(row.getReportStatus())) {
            result.setDiagnosticStatus("FAILED");
            result.setNextAction("RETRY_DIAGNOSTIC_RESULT");
        } else {
            throw invalidState();
        }
    }

    private OnboardingException invalidState() {
        return new OnboardingException("学习状态异常，请联系管理员");
    }
}
