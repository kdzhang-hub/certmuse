package org.dromara.certmuse.assessment.service;

import org.dromara.certmuse.assessment.domain.bo.SubmitDailyTaskItemBo;
import org.dromara.certmuse.assessment.domain.vo.CompleteDailyTaskVo;
import org.dromara.certmuse.assessment.domain.vo.DailyTaskItemVo;
import org.dromara.certmuse.assessment.domain.vo.DailyTaskSessionVo;
import org.dromara.certmuse.assessment.domain.vo.SubmitDailyTaskItemVo;

/** U11 daily-task answering use cases. */
public interface DailyTaskService {
    DailyTaskSessionVo session(long userId, String sessionId);
    DailyTaskItemVo item(long userId, String sessionId, int questionOrder);
    SubmitDailyTaskItemVo submit(long userId, String sessionId, int questionOrder, String requestId,
                                 SubmitDailyTaskItemBo command);
    CompleteDailyTaskVo complete(long userId, String sessionId, String requestId);
}
