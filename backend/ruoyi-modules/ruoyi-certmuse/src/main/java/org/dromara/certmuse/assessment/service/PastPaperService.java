package org.dromara.certmuse.assessment.service;

import org.dromara.certmuse.assessment.domain.bo.PastPaperAnswerBo;
import org.dromara.certmuse.assessment.domain.bo.PastPaperStartBo;
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
import org.dromara.certmuse.assessment.domain.vo.PastPaperListVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperPreviewVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperRevealVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperSetupVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperStartVo;
import org.dromara.certmuse.assessment.domain.vo.CompletePastPaperPracticeVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperPracticeItemVo;
import org.dromara.certmuse.assessment.domain.vo.PastPaperPracticeSessionVo;
import org.dromara.certmuse.assessment.domain.vo.StartPastPaperPracticeVo;
import org.dromara.certmuse.assessment.domain.vo.SubmitPastPaperPracticeItemVo;
import org.dromara.common.core.domain.PageResult;

/** Learner-facing U15 browsing and formal past-paper aggregate. */
public interface PastPaperService {
    PastPaperSetupVo setup(Long userId);
    PageResult<PastPaperListVo> list(Long userId, String certificationId, String subjectId, String keyword,
                                     Integer pageNum, Integer pageSize);
    PastPaperListVo detail(long collectionId);
    PastPaperPreviewVo preview(long collectionId);
    PastPaperRevealVo reveal(long userId, long collectionId, int questionOrder, String requestId);
    StartPastPaperPracticeVo startPractice(long userId, long collectionId, String requestId, PastPaperStartBo command);
    PastPaperStartVo startExam(long userId, long collectionId, String requestId, PastPaperStartBo command);
    PastPaperPracticeSessionVo practiceSession(long userId, long sessionId);
    PastPaperPracticeItemVo practiceItem(long userId, long sessionId, int questionOrder);
    SubmitPastPaperPracticeItemVo submitPractice(long userId, long sessionId, int questionOrder, String requestId, PastPaperAnswerBo command);
    CompletePastPaperPracticeVo completePractice(long userId, long sessionId, String requestId);
    FormalExamSessionVo formalSession(long userId, long sessionId);
    FormalExamItemVo formalItem(long userId, long sessionId, int questionOrder);
    FormalExamSessionVo saveFormalDraft(long userId, long sessionId, int questionOrder, String requestId, FormalExamDraftBo command);
    FormalExamTimerEventVo formalTimerEvent(long userId, long sessionId, String requestId, FormalExamTimerEventBo command);
    FormalExamSessionVo pauseFormal(long userId, long sessionId, String requestId, FormalExamPauseBo command);
    FormalExamFinishCheckVo formalFinishCheck(long userId, long sessionId);
    FormalExamFinishVo finishFormal(long userId, long sessionId, String requestId, FormalExamFinishBo command);
    FormalExamStatusVo formalStatus(long userId, long sessionId);
    FormalExamResultVo formalResult(long userId, long sessionId);
    FormalExamStatusVo regenerateFormal(long userId, long sessionId, String requestId);
    void autoFinishExpired(int limit);
    void dispatchResults(int limit);
    void processPracticeSubjectiveGrading(int limit);
}
