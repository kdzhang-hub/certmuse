package org.dromara.certmuse.assessment.service;

import org.dromara.certmuse.assessment.domain.bo.StartKnowledgePracticeBo;
import org.dromara.certmuse.assessment.domain.bo.SubmitKnowledgePracticeItemBo;
import org.dromara.certmuse.assessment.domain.vo.SubmitKnowledgePracticeItemVo;
import org.dromara.certmuse.assessment.domain.vo.CompleteKnowledgePracticeVo;
import org.dromara.certmuse.assessment.domain.vo.KnowledgePracticeSetupVo;
import org.dromara.certmuse.assessment.domain.vo.KnowledgePracticeSessionVo;
import org.dromara.certmuse.assessment.domain.vo.KnowledgePracticeItemVo;
import org.dromara.certmuse.assessment.domain.vo.StartKnowledgePracticeVo;

/** Learner-facing U08 knowledge-practice use case. */
public interface KnowledgePracticeService {
    /** Returns the current-goal visible knowledge tree and real-time eligible counts. */
    KnowledgePracticeSetupVo setup(long userId);

    /** Atomically freezes an eligible visible node into a self-practice session. */
    StartKnowledgePracticeVo start(long userId, String requestId, StartKnowledgePracticeBo command);

    /** Starts or reads an owned self-practice session and its answer card. */
    KnowledgePracticeSessionVo session(long userId, long sessionId);

    /** Reads one frozen item without consulting mutable question content. */
    KnowledgePracticeItemVo item(long userId, long sessionId, int questionOrder);

    /** Persists, grades and settles one frozen single-choice answer. */
    SubmitKnowledgePracticeItemVo submit(long userId, long sessionId, int questionOrder, String requestId,
                                         SubmitKnowledgePracticeItemBo command);

    /** Ends an owned self-practice session without creating facts for unanswered items. */
    CompleteKnowledgePracticeVo complete(long userId, long sessionId, String requestId);
}
