package org.dromara.certmuse.profile.service;

import org.dromara.certmuse.profile.domain.vo.OverallScoreVo;

/** Read-only learner-profile use cases. */
public interface LearningProfileQueryService {
    /**
     * Returns the persisted score for the caller's current active or paused goal.
     *
     * @param userId authenticated learner ID
     * @return qualification name and nullable persisted score projection
     */
    OverallScoreVo overallScore(long userId);
}
