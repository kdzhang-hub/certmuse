package org.dromara.certmuse.learning.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.dromara.certmuse.learning.domain.LearningTaskCandidateRow;
import org.dromara.certmuse.learning.domain.LearningTaskGoalRow;
import org.dromara.certmuse.learning.domain.LearningTaskQuestionRow;
import org.dromara.certmuse.learning.domain.LearningTaskRow;
import org.dromara.certmuse.learning.domain.LearningTaskSessionRow;
import org.dromara.certmuse.learning.domain.bo.LearningTaskQueryBo;
import org.dromara.certmuse.learning.domain.vo.LearningTaskListItemVo;
import org.dromara.certmuse.learning.mapper.LearningTaskMapper;
import org.dromara.certmuse.learning.support.LearningContentImageUrlService;
import org.dromara.certmuse.learning.support.LearningTaskException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

@Tag("dev")
class LearningTaskServiceImplTest {
    private static final String REQUEST_ID = "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd";
    private final LearningTaskMapper mapper = Mockito.mock(LearningTaskMapper.class);
    private final PlatformTransactionManager transactionManager = Mockito.mock(PlatformTransactionManager.class);
    private final LearningContentImageUrlService learningContentImageUrlService = Mockito.mock(LearningContentImageUrlService.class);
    private final LearningTaskServiceImpl service = new LearningTaskServiceImpl(mapper,
        JsonMapper.builder().build(), transactionManager, learningContentImageUrlService);

    @Test
    void pageUsesUnfilteredAvailableCountAndAddsFixedPhases() {
        LearningTaskGoalRow goal = goal();
        LearningTaskListItemVo row = new LearningTaskListItemVo();
        row.setId("30"); row.setUpdatedAt(OffsetDateTime.now());
        LearningTaskQueryBo query = new LearningTaskQueryBo();
        query.setKeyword("  架构  ");
        when(mapper.selectActiveGoal(1L)).thenReturn(goal);
        when(mapper.countIncompleteTasks(1L, 10L)).thenReturn(2);
        when(mapper.countTaskPage(1L, 10L, "架构")).thenReturn(1L);
        when(mapper.selectTaskPage(1L, 10L, "架构", 0, 10)).thenReturn(List.of(row));

        var result = service.page(1L, query);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.isCanShowSupplement()).isFalse();
        assertThat(row.getPhaseSummary()).containsExactly("知识点学习", "知识点练习");
    }

    @Test
    void rejectsBlankKeyword() {
        when(mapper.selectActiveGoal(1L)).thenReturn(goal());
        LearningTaskQueryBo query = new LearningTaskQueryBo();
        query.setKeyword("   ");
        assertThatThrownBy(() -> service.page(1L, query)).isInstanceOf(LearningTaskException.class)
            .satisfies(error -> assertThat(((LearningTaskException) error).errorCode())
                .isEqualTo("TASK_REQUEST_INVALID"));
    }

    @Test
    void anotherTaskSessionReturnsCurrentTaskAfterRequiredLockOrder() {
        LearningTaskGoalRow goal = goal();
        LearningTaskSessionRow active = new LearningTaskSessionRow();
        active.setId(50L); active.setSourceTaskId(31L); active.setStatus("created");
        when(mapper.lockActiveGoal(1L)).thenReturn(goal);
        when(mapper.insertIdempotency(anyLong(), anyString(), anyString(), anyString())).thenReturn(1);
        when(mapper.lockActiveDailySession(1L, 10L)).thenReturn(active);
        when(mapper.lockOwnedTask(1L, 10L, 30L)).thenReturn(task());

        assertThatThrownBy(() -> service.launch(1L, "30", REQUEST_ID))
            .isInstanceOf(LearningTaskException.class)
            .satisfies(error -> assertThat(((LearningTaskException) error).errorCode())
                .isEqualTo("TASK_SESSION_IN_PROGRESS"));
        InOrder order = inOrder(mapper);
        order.verify(mapper).lockActiveGoal(1L);
        order.verify(mapper).selectIdempotency(anyString(), anyString());
        order.verify(mapper).insertIdempotency(anyLong(), anyString(), anyString(), anyString());
        order.verify(mapper).lockActiveDailySession(1L, 10L);
        order.verify(mapper).lockOwnedTask(1L, 10L, 30L);
    }

    @Test
    void firstLaunchCreatesOneSessionEightFrozenQuestionsAndLearningAttempt() {
        when(mapper.lockActiveGoal(1L)).thenReturn(goal());
        when(mapper.insertIdempotency(anyLong(), anyString(), anyString(), anyString())).thenReturn(1);
        when(mapper.lockOwnedTask(1L, 10L, 30L)).thenReturn(task());
        when(mapper.insertSession(anyLong(), anyLong(), any(), any(), anyString())).thenReturn(1);
        when(mapper.selectTaskQuestions(42L)).thenReturn(questions());

        var result = service.launch(1L, "30", REQUEST_ID.toUpperCase());

        assertThat(result.nextAction()).isEqualTo("OPEN_LEARNING_CONTENT");
        assertThat(result.sessionId()).isNull();
        verify(mapper, times(8)).insertSessionQuestion(anyLong(), anyLong(),
            Mockito.intThat(value -> value >= 1 && value <= 8), any());
        verify(mapper).insertTaskAttempt(anyLong(), Mockito.eq(41L), anyLong());
    }

    @Test
    void firstLaunchCopiesAllFrozenQuestionsWhenTaskHasFewerThanEight() {
        LearningTaskRow task = task();
        task.setQuestionCount(3);
        when(mapper.lockActiveGoal(1L)).thenReturn(goal());
        when(mapper.insertIdempotency(anyLong(), anyString(), anyString(), anyString())).thenReturn(1);
        when(mapper.lockOwnedTask(1L, 10L, 30L)).thenReturn(task);
        when(mapper.insertSession(anyLong(), anyLong(), any(), any(), anyString())).thenReturn(1);
        when(mapper.selectTaskQuestions(42L)).thenReturn(questions().subList(0, 3));

        var result = service.launch(1L, "30", REQUEST_ID);

        assertThat(result.nextAction()).isEqualTo("OPEN_LEARNING_CONTENT");
        verify(mapper, times(3)).insertSessionQuestion(anyLong(), anyLong(),
            Mockito.intThat(value -> value >= 1 && value <= 3), any());
        verify(mapper).insertTaskAttempt(anyLong(), Mockito.eq(41L), anyLong());
    }

    @Test
    void firstLaunchRejectsFrozenQuestionCountThatDoesNotMatchTaskSnapshot() {
        LearningTaskRow task = task();
        task.setQuestionCount(3);
        when(mapper.lockActiveGoal(1L)).thenReturn(goal());
        when(mapper.insertIdempotency(anyLong(), anyString(), anyString(), anyString())).thenReturn(1);
        when(mapper.lockOwnedTask(1L, 10L, 30L)).thenReturn(task);
        when(mapper.insertSession(anyLong(), anyLong(), any(), any(), anyString())).thenReturn(1);
        when(mapper.selectTaskQuestions(42L)).thenReturn(questions().subList(0, 2));

        assertThatThrownBy(() -> service.launch(1L, "30", REQUEST_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("frozen question count");
    }

    @Test
    void supplementCreatesTaskWithAllAvailableQuestionsWhenFewerThanEightExist() throws Exception {
        LearningTaskCandidateRow candidate = candidate();
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(mapper.selectActiveGoal(1L)).thenReturn(goal());
        when(mapper.insertIdempotency(anyLong(), anyString(), anyString(), anyString())).thenReturn(1);
        when(mapper.lockGoalById(10L)).thenReturn(goal());
        when(mapper.countIncompleteTasks(1L, 10L)).thenReturn(0);
        when(mapper.selectUniquePublishedRuleVersion()).thenReturn(60L);
        when(mapper.insertBatch(anyLong(), eq(1L), eq(10L), eq("manual"), eq(0), eq(4), eq(60L),
            eq(REQUEST_ID), anyString(), anyString())).thenReturn(1);
        when(mapper.countPublishedRuleVersions()).thenReturn(1);
        when(mapper.selectCandidates(1L, 10L, 12L, "daily_task_learning_content/1.0", 16)).thenReturn(List.of(candidate));
        when(mapper.selectCandidateQuestions(1L, 10L, 12L, 70L, 8))
            .thenReturn(questions().subList(0, 3));

        var result = service.supplement(1L, REQUEST_ID);

        assertThat(result.createdCount()).isEqualTo(1);
        verify(mapper).selectCandidateQuestions(1L, 10L, 12L, 70L, 8);
        ArgumentCaptor<String> targetData = ArgumentCaptor.forClass(String.class);
        verify(mapper).insertTaskItem(anyLong(), anyLong(), eq(2), eq("question"), eq(candidate),
            eq(3), targetData.capture());
        assertThat(JsonMapper.builder().build().readTree(targetData.getValue()).path("questionCount").asInt())
            .isEqualTo(3);
        verify(mapper, times(3)).insertTaskQuestion(anyLong(), anyLong(),
            Mockito.intThat(value -> value >= 1 && value <= 3), any());
    }

    private LearningTaskGoalRow goal() {
        LearningTaskGoalRow row = new LearningTaskGoalRow();
        row.setId(10L); row.setUserId(1L); row.setCertificationId(11L); row.setSyllabusVersionId(12L);
        row.setTargetExamYear(2026); row.setTargetExamMonth(11); return row;
    }

    private LearningTaskRow task() {
        LearningTaskRow row = new LearningTaskRow();
        row.setId(30L); row.setUserId(1L); row.setGoalId(10L); row.setExamSubjectId(20L);
        row.setRuleVersionId(60L); row.setKnowledgeItemId(41L); row.setQuestionItemId(42L);
        row.setQuestionCount(8); return row;
    }

    private LearningTaskCandidateRow candidate() {
        LearningTaskCandidateRow row = new LearningTaskCandidateRow();
        row.setKnowledgePointId(70L); row.setExamSubjectId(20L); row.setKnowledgeName("架构风格");
        row.setProfileStatus("weak"); row.setContentSnapshot("{\"schema_version\":\"1.0\"}");
        return row;
    }

    private List<LearningTaskQuestionRow> questions() {
        List<LearningTaskQuestionRow> rows = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            LearningTaskQuestionRow row = new LearningTaskQuestionRow();
            row.setQuestionId(100L + index); row.setQuestionRevisionId(200L + index);
            row.setExamSubjectId(20L); row.setEvidenceGroupKey("Q:" + index); row.setDifficulty("medium");
            row.setEstimatedSeconds(60); row.setPresentationSnapshot("{\"schema_version\":\"1.0\"}");
            row.setGradingSnapshot("{\"schema_version\":\"1.0\"}");
            row.setKnowledgeSnapshot("{\"schema_version\":\"1.0\"}");
            rows.add(row);
        }
        return rows;
    }
}
