package org.dromara.certmuse.assessment.service;

import org.dromara.certmuse.assessment.domain.bo.SimulationQueryBo;
import org.dromara.certmuse.assessment.domain.bo.StartSimulationSessionBo;
import org.dromara.certmuse.assessment.domain.bo.FormalExamDraftBo;
import org.dromara.certmuse.assessment.domain.bo.FormalExamFinishBo;
import org.dromara.certmuse.assessment.domain.bo.FormalExamPauseBo;
import org.dromara.certmuse.assessment.domain.bo.FormalExamTimerEventBo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamFinishCheckVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamFinishVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamItemVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamResultVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamSessionVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamStatusVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamTimerEventVo;
import org.dromara.certmuse.assessment.domain.vo.SimulationDetailVo;
import org.dromara.certmuse.assessment.domain.vo.SimulationListItemVo;
import org.dromara.certmuse.assessment.domain.vo.SimulationPreviewVo;
import org.dromara.certmuse.assessment.domain.vo.SimulationSetupVo;
import org.dromara.certmuse.assessment.domain.vo.StartSimulationSessionVo;
import org.dromara.common.core.domain.PageResult;

/** Learner-facing U13 simulation display and gated start use cases. */
public interface SimulationService {
    /** Returns current-goal context and all published simulation filter options. */
    SimulationSetupVo setup(long userId);

    /** Returns a stable page of current published simulations. */
    PageResult<SimulationListItemVo> list(long userId, SimulationQueryBo query);

    /** Returns safe instructions for one current published simulation. */
    SimulationDetailVo detail(long userId, String collectionId);

    /** Returns ordered published question stems only for the explicitly enabled paper preview. */
    SimulationPreviewVo preview(long userId, String collectionId);

    /** Validates the request and rejects start while the formal-exam chain is unavailable. */
    StartSimulationSessionVo start(long userId, String collectionId, String requestId,
                                   StartSimulationSessionBo command);
    FormalExamSessionVo session(long userId, long sessionId);
    FormalExamItemVo item(long userId, long sessionId, int questionOrder);
    FormalExamSessionVo saveDraft(long userId, long sessionId, int questionOrder, String requestId, FormalExamDraftBo command);
    FormalExamTimerEventVo timerEvent(long userId, long sessionId, String requestId, FormalExamTimerEventBo command);
    FormalExamSessionVo pause(long userId, long sessionId, String requestId, FormalExamPauseBo command);
    FormalExamFinishCheckVo finishCheck(long userId, long sessionId);
    FormalExamFinishVo finish(long userId, long sessionId, String requestId, FormalExamFinishBo command);
    FormalExamStatusVo status(long userId, long sessionId);
    FormalExamResultVo result(long userId, long sessionId);
    FormalExamStatusVo regenerate(long userId, long sessionId, String requestId);
}
