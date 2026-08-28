package org.dromara.certmuse.learning.service;

import org.dromara.certmuse.learning.domain.bo.HistoryExamQueryBo;
import org.dromara.certmuse.learning.domain.bo.HistoryQuestionQueryBo;
import org.dromara.certmuse.learning.domain.bo.HistoryPracticeQueryBo;
import org.dromara.certmuse.learning.domain.bo.HistoryTaskQueryBo;
import org.dromara.certmuse.learning.domain.vo.HistoryExamDetailVo;
import org.dromara.certmuse.learning.domain.vo.HistoryExamPageVo;
import org.dromara.certmuse.learning.domain.vo.HistoryGoalsVo;
import org.dromara.certmuse.learning.domain.vo.HistoryQuestionDetailVo;
import org.dromara.certmuse.learning.domain.vo.HistoryQuestionPageVo;
import org.dromara.certmuse.learning.domain.vo.HistoryPracticeDetailVo;
import org.dromara.certmuse.learning.domain.vo.HistoryPracticePageVo;
import org.dromara.certmuse.learning.domain.vo.HistoryTaskDetailVo;
import org.dromara.certmuse.learning.domain.vo.HistoryTaskPageVo;

/** Read-only student learning-history use cases. */
public interface LearningHistoryService {
    /** Returns learning goals that contain visible history facts. */
    HistoryGoalsVo goals(long userId);
    /** Returns completed daily tasks within the selected owned goal. */
    HistoryTaskPageVo tasks(long userId, HistoryTaskQueryBo query);
    /** Returns one completed daily task and its frozen questions. */
    HistoryTaskDetailVo task(long userId, String taskId, String goalId);
    /** Returns question history aggregated by logical question. */
    HistoryQuestionPageVo questions(long userId, HistoryQuestionQueryBo query);
    /** Returns all valid attempts and correction events for one question. */
    HistoryQuestionDetailVo question(long userId, String questionId, String goalId);
    /** Returns completed knowledge and past-paper practice sessions. */
    HistoryPracticePageVo practices(long userId, HistoryPracticeQueryBo query);
    /** Returns one owned completed practice with its frozen questions. */
    HistoryPracticeDetailVo practice(long userId, String sessionId, String goalId);
    /** Returns completed initial diagnosis, past-paper, and simulation exams by session. */
    HistoryExamPageVo exams(long userId, HistoryExamQueryBo query);
    /** Returns one owned completed exam and its frozen question results. */
    HistoryExamDetailVo exam(long userId, String sessionId, String goalId);
}
