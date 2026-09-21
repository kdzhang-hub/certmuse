package org.dromara.certmuse.assessment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeAnsweringSessionRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeItemRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeNavigationRow;
import org.dromara.certmuse.assessment.domain.bo.SubmitDailyTaskItemBo;
import org.dromara.certmuse.assessment.mapper.DailyTaskMapper;
import org.dromara.certmuse.assessment.service.ChoiceAnswerSettlementService;
import org.dromara.certmuse.learning.service.LearningTaskService;
import org.dromara.certmuse.question.service.QuestionImageUrlService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

@Tag("dev")
class DailyTaskServiceImplTest {
    private final DailyTaskMapper mapper = Mockito.mock(DailyTaskMapper.class);
    private final ChoiceAnswerSettlementService settlementService = Mockito.mock(ChoiceAnswerSettlementService.class);
    private final DailyTaskServiceImpl service = new DailyTaskServiceImpl(mapper, JsonMapper.builder().build(),
        Mockito.mock(QuestionImageUrlService.class), settlementService, Mockito.mock(LearningTaskService.class));

    @Test
    void submitSettlesLastAnswerAndStoresVersionedIdempotencyResponse() throws Exception {
        KnowledgePracticeItemRow item = item();
        when(mapper.insertIdempotency(anyLong(), anyString(), anyString(), anyString())).thenReturn(1);
        when(mapper.lockSession(10L, 1L)).thenReturn(session("in_progress", 1, 0));
        when(mapper.selectItem(10L, 1L, 1)).thenReturn(item);
        when(mapper.insertAttempt(anyLong(), anyLong(), anyLong(), anyString())).thenReturn(1);
        when(mapper.countSubmittedItems(10L)).thenReturn(1);
        when(mapper.submitSession(10L, 1L)).thenReturn(1);

        var result = service.submit(1L, "10", 1, "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd", command("A"));

        assertThat(result.submission().correct()).isTrue();
        verify(settlementService).settle(eq(item), anyLong(), anyBoolean(), anyString());
        verify(mapper).submitSession(10L, 1L);
        ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(mapper).succeedIdempotency(anyLong(), anyString(), anyLong(), response.capture());
        assertThat(response.getValue()).contains("daily_task_action_idempotency/1.0", "\"response\"");
    }

    @Test
    void sessionReturnsFrozenTaskTitleAndPersistedNavigationResults() {
        KnowledgePracticeAnsweringSessionRow row = session("in_progress", 3, 2);
        row.setTaskTitle("网络安全专项学习");
        when(mapper.selectSession(10L, 1L)).thenReturn(row);
        when(mapper.selectNavigation(10L)).thenReturn(List.of(
            navigation(1, true, true), navigation(2, true, false), navigation(3, false, null)));

        var result = service.session(1L, "10");

        assertThat(result.taskTitle()).isEqualTo("网络安全专项学习");
        assertThat(result.navigation()).extracting(item -> item.state())
            .containsExactly("SUBMITTED_CORRECT", "SUBMITTED_INCORRECT", "UNANSWERED");
    }

    @Test
    void completeTransitionsOnlyAfterAllAnswersAndCompletesPracticeItem() {
        LearningTaskService learningTaskService = Mockito.mock(LearningTaskService.class);
        DailyTaskServiceImpl completingService = new DailyTaskServiceImpl(mapper, JsonMapper.builder().build(),
            Mockito.mock(QuestionImageUrlService.class), settlementService, learningTaskService);
        when(mapper.insertIdempotency(anyLong(), anyString(), anyString(), anyString())).thenReturn(1);
        when(mapper.lockSession(10L, 1L)).thenReturn(session("submitted", 1, 1));
        when(mapper.selectSession(10L, 1L)).thenReturn(session("submitted", 1, 1));
        when(mapper.settleSession(10L, 1L)).thenReturn(1);
        when(mapper.completeSession(10L, 1L)).thenReturn(1);

        var result = completingService.complete(1L, "10", "5d45fcea-bd6e-46e4-9230-230bb50daf32");

        assertThat(result.sessionStatus()).isEqualTo("COMPLETED");
        verify(learningTaskService).completePracticeItem(1L, 10L);
        verify(mapper).settleSession(10L, 1L);
        verify(mapper).completeSession(10L, 1L);
    }

    private KnowledgePracticeAnsweringSessionRow session(String status, int total, int submitted) {
        KnowledgePracticeAnsweringSessionRow row = new KnowledgePracticeAnsweringSessionRow();
        row.setId(10L); row.setUserId(1L); row.setGoalId(20L); row.setRuleVersionId(40L);
        row.setStatus(status); row.setTotalCount(total); row.setSubmittedCount(submitted);
        return row;
    }

    private KnowledgePracticeItemRow item() {
        KnowledgePracticeItemRow row = new KnowledgePracticeItemRow();
        row.setSessionQuestionId(11L); row.setSessionId(10L); row.setUserId(1L); row.setGoalId(20L);
        row.setRuleVersionId(40L); row.setExamSubjectId(50L); row.setEvidenceGroupKey("Q:1");
        row.setDifficultySnapshot("medium"); row.setQuestionOrder(1); row.setTotalCount(1);
        row.setPresentationSnapshot("{\"stem\":\"s\",\"options\":[{\"label\":\"A\",\"content\":\"a\"}],\"images\":[]}");
        row.setGradingSnapshot("{\"answer\":{\"value\":[\"A\"]},\"analysis\":\"ok\"}");
        row.setKnowledgeSnapshot("{\"items\":[]}");
        return row;
    }

    private KnowledgePracticeNavigationRow navigation(int order, boolean submitted, Boolean correct) {
        KnowledgePracticeNavigationRow row = new KnowledgePracticeNavigationRow();
        row.setQuestionOrder(order); row.setSubmitted(submitted); row.setCorrect(correct);
        return row;
    }

    private SubmitDailyTaskItemBo command(String value) {
        SubmitDailyTaskItemBo command = new SubmitDailyTaskItemBo();
        SubmitDailyTaskItemBo.AnswerBo answer = new SubmitDailyTaskItemBo.AnswerBo();
        answer.setValue(List.of(value));
        command.setAnswer(answer);
        return command;
    }
}
