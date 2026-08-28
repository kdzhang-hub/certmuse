package org.dromara.certmuse.assessment.service;

import org.dromara.certmuse.assessment.domain.bo.DiagnosticDraftBo;
import org.dromara.certmuse.assessment.domain.bo.DiagnosticFinishBo;
import org.dromara.certmuse.assessment.domain.bo.DiagnosticPauseBo;
import org.dromara.certmuse.assessment.domain.bo.DiagnosticStartBo;
import org.dromara.certmuse.assessment.domain.bo.DiagnosticTimerEventBo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticDraftVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticFinishCheckVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticFinishVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticItemVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticPreflightVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticReportVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticSessionVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticStartVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticStatusVo;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticTimerEventVo;

/** Learner-facing first diagnostic lifecycle. */
public interface DiagnosticService {
    DiagnosticPreflightVo preflight(Long userId);
    DiagnosticStartVo start(Long userId, String requestId, DiagnosticStartBo command);
    DiagnosticSessionVo session(Long userId, long sessionId);
    DiagnosticItemVo item(Long userId, long sessionId, int questionOrder);
    /** Records a visibility event and returns server-confirmed timing facts. */
    DiagnosticTimerEventVo timerEvent(Long userId, long sessionId, String requestId, DiagnosticTimerEventBo command);
    DiagnosticDraftVo saveDraft(Long userId, long sessionId, int questionOrder, String requestId, DiagnosticDraftBo command);
    DiagnosticSessionVo pause(Long userId, long sessionId, String requestId, DiagnosticPauseBo command);
    DiagnosticFinishCheckVo finishCheck(Long userId, long sessionId);
    DiagnosticFinishVo finish(Long userId, long sessionId, String requestId, DiagnosticFinishBo command);
    DiagnosticStatusVo status(Long userId, long sessionId);
    DiagnosticStatusVo regenerate(Long userId, long sessionId, String requestId);
    DiagnosticReportVo report(Long userId, long sessionId);
    boolean hasInProgressDiagnostic(Long userId, long goalId);
}
