package org.dromara.certmuse.learning.mapper;

import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.learning.domain.HistoryAttemptRow;
import org.dromara.certmuse.learning.domain.HistoryCorrectionEventRow;
import org.dromara.certmuse.learning.domain.HistoryExamQuestionRow;
import org.dromara.certmuse.learning.domain.HistoryExamRow;
import org.dromara.certmuse.learning.domain.HistoryGoalRow;
import org.dromara.certmuse.learning.domain.HistoryPracticeItemRow;
import org.dromara.certmuse.learning.domain.HistoryPracticeSessionRow;
import org.dromara.certmuse.learning.domain.HistoryTaskRow;

/** PostgreSQL aggregate queries for read-only learner history. */
public interface LearningHistoryMapper {
    Long selectActiveGoal(@Param("userId") long userId);
    boolean existsOwnedGoal(@Param("userId") long userId, @Param("goalId") long goalId);
    List<HistoryGoalRow> selectGoals(@Param("userId") long userId);
    long countTasks(@Param("userId") long userId, @Param("goalId") long goalId,
                    @Param("keyword") String keyword, @Param("subjectId") Long subjectId,
                    @Param("knowledgePointId") Long knowledgePointId,
                    @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
    List<HistoryTaskRow> selectTasks(@Param("userId") long userId, @Param("goalId") long goalId,
                                     @Param("keyword") String keyword, @Param("subjectId") Long subjectId,
                                     @Param("knowledgePointId") Long knowledgePointId,
                                     @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to,
                                     @Param("offset") int offset, @Param("limit") int limit);
    HistoryTaskRow selectTask(@Param("userId") long userId, @Param("goalId") long goalId,
                              @Param("taskId") long taskId);
    List<HistoryAttemptRow> selectTaskAttempts(@Param("userId") long userId, @Param("goalId") long goalId,
                                               @Param("taskId") long taskId);
    long countInconsistentQuestions(@Param("userId") long userId, @Param("goalId") long goalId,
                                    @Param("keyword") String keyword, @Param("questionType") String questionType,
                                    @Param("difficulty") String difficulty,
                                    @Param("knowledgePointId") Long knowledgePointId,
                                    @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
    long countQuestions(@Param("userId") long userId, @Param("goalId") long goalId,
                        @Param("keyword") String keyword, @Param("questionType") String questionType,
                        @Param("difficulty") String difficulty, @Param("knowledgePointId") Long knowledgePointId,
                        @Param("correctionStatus") String correctionStatus,
                        @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
    List<HistoryAttemptRow> selectQuestions(@Param("userId") long userId, @Param("goalId") long goalId,
                                            @Param("keyword") String keyword, @Param("questionType") String questionType,
                                            @Param("difficulty") String difficulty, @Param("knowledgePointId") Long knowledgePointId,
                                            @Param("correctionStatus") String correctionStatus,
                                            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to,
                                            @Param("offset") int offset, @Param("limit") int limit);
    List<HistoryAttemptRow> selectQuestionAttempts(@Param("userId") long userId, @Param("goalId") long goalId,
                                                   @Param("questionId") long questionId);
    List<HistoryCorrectionEventRow> selectCorrectionEvents(@Param("userId") long userId,
                                                           @Param("goalId") long goalId,
                                                           @Param("questionId") long questionId);
    long countPractices(@Param("userId") long userId, @Param("goalId") long goalId,
                        @Param("sessionType") String sessionType, @Param("sessionId") Long sessionId,
                        @Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to);
    List<HistoryPracticeSessionRow> selectPractices(@Param("userId") long userId, @Param("goalId") long goalId,
                                                     @Param("sessionType") String sessionType,
                                                     @Param("sessionId") Long sessionId,
                                                     @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to,
                                                     @Param("offset") int offset, @Param("limit") int limit);
    HistoryPracticeSessionRow selectPractice(@Param("userId") long userId, @Param("goalId") long goalId,
                                              @Param("sessionId") long sessionId,
                                              @Param("sessionType") String sessionType,
                                              @Param("from") OffsetDateTime from,
                                              @Param("to") OffsetDateTime to);
    List<HistoryPracticeItemRow> selectPracticeItems(@Param("userId") long userId, @Param("goalId") long goalId,
                                                      @Param("sessionId") long sessionId);
    long countInconsistentExams(@Param("userId") long userId, @Param("goalId") long goalId);
    long countExams(@Param("userId") long userId, @Param("goalId") long goalId,
                    @Param("examType") String examType, @Param("from") OffsetDateTime from,
                    @Param("to") OffsetDateTime to);
    List<HistoryExamRow> selectExams(@Param("userId") long userId, @Param("goalId") long goalId,
                                     @Param("examType") String examType, @Param("from") OffsetDateTime from,
                                     @Param("to") OffsetDateTime to, @Param("offset") int offset,
                                     @Param("limit") int limit);
    HistoryExamRow selectExam(@Param("userId") long userId, @Param("goalId") long goalId,
                              @Param("sessionId") long sessionId);
    List<HistoryExamQuestionRow> selectExamQuestions(@Param("userId") long userId,
                                                      @Param("goalId") long goalId,
                                                      @Param("sessionId") long sessionId);
}
