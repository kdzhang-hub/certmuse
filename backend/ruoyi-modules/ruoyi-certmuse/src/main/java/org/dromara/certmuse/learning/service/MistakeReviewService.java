package org.dromara.certmuse.learning.service;

import org.dromara.certmuse.learning.domain.bo.CreateMistakeCorrectionSessionBo;
import org.dromara.certmuse.learning.domain.bo.SubmitMistakeCorrectionItemBo;
import org.dromara.certmuse.learning.domain.vo.CompleteMistakeCorrectionSessionVo;
import org.dromara.certmuse.learning.domain.vo.CreateMistakeCorrectionSessionVo;
import org.dromara.certmuse.learning.domain.vo.MistakeCorrectionItemVo;
import org.dromara.certmuse.learning.domain.vo.MistakeCorrectionSessionVo;
import org.dromara.certmuse.learning.domain.vo.MistakeDetailVo;
import org.dromara.certmuse.learning.domain.vo.MistakeListVo;
import org.dromara.certmuse.learning.domain.vo.SubmitMistakeCorrectionItemVo;

/** Learner-facing frozen mistake review and original-question correction use case. */
public interface MistakeReviewService {
    MistakeListVo list(long userId, String keyword, String status, String knowledgePointId, String source,
                       Integer minWrongCount, Integer pageNum, Integer pageSize);
    MistakeDetailVo detail(long userId, long questionId);
    CreateMistakeCorrectionSessionVo create(long userId, String requestId, CreateMistakeCorrectionSessionBo command);
    MistakeCorrectionSessionVo session(long userId, long sessionId);
    MistakeCorrectionItemVo item(long userId, long sessionId, int questionOrder);
    SubmitMistakeCorrectionItemVo submit(long userId, long sessionId, int questionOrder, String requestId,
                                         SubmitMistakeCorrectionItemBo command);
    CompleteMistakeCorrectionSessionVo complete(long userId, long sessionId, String requestId);
}
