package org.dromara.certmuse.learning.service;

import org.dromara.certmuse.learning.domain.bo.CreateLearningGoalBo;
import org.dromara.certmuse.learning.domain.bo.SwitchLearningGoalBo;
import org.dromara.certmuse.learning.domain.vo.CreateLearningGoalResultVo;
import org.dromara.certmuse.learning.domain.vo.GoalSwitchOptionsVo;
import org.dromara.certmuse.learning.domain.vo.LearningGoalOptionsVo;
import org.dromara.certmuse.learning.domain.vo.SwitchLearningGoalResultVo;

/** Entry service for the learner's first learning-goal flow. */
public interface LearningGoalService {
    /** Loads the options shown by the first learning-goal form. */
    LearningGoalOptionsVo options(long userId);

    /** Creates a first active goal and returns the fixed next action. */
    CreateLearningGoalResultVo create(long userId, String requestId, CreateLearningGoalBo command);

    GoalSwitchOptionsVo switchOptions(long userId);

    SwitchLearningGoalResultVo switchGoal(long userId, String requestId, SwitchLearningGoalBo command);
}
