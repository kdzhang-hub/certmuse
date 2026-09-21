package org.dromara.certmuse.assessment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeAnsweringSessionRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeItemRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeNavigationRow;
import org.dromara.certmuse.assessment.domain.bo.SubmitKnowledgePracticeItemBo;
import org.dromara.certmuse.assessment.mapper.KnowledgePracticeMapper;
import org.dromara.certmuse.assessment.service.ChoiceAnswerSettlementService;
import org.dromara.certmuse.question.service.QuestionImageUrlService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

@Tag("dev")
class KnowledgePracticeAnsweringServiceTest {
    private final KnowledgePracticeMapper mapper = Mockito.mock(KnowledgePracticeMapper.class);
    private final QuestionImageUrlService imageUrlService = Mockito.mock(QuestionImageUrlService.class);
    private final KnowledgePracticeServiceImpl service = new KnowledgePracticeServiceImpl(
        mapper, JsonMapper.builder().build(), imageUrlService,
        new ChoiceAnswerSettlementService(mapper,
            Mockito.mock(org.dromara.certmuse.learning.service.MistakeFactRecorder.class), JsonMapper.builder().build()));

    @Test
    void startsCreatedSessionAndReturnsStableNavigation() {
        KnowledgePracticeAnsweringSessionRow created = session("created");
        KnowledgePracticeAnsweringSessionRow active = session("in_progress");
        when(mapper.selectAnsweringSession(10L, 1L)).thenReturn(created, active);
        KnowledgePracticeNavigationRow navigation = new KnowledgePracticeNavigationRow();
        navigation.setQuestionOrder(1); navigation.setSubmitted(false);
        when(mapper.selectNavigation(10L)).thenReturn(List.of(navigation));

        var result = service.session(1L, 10L);

        assertThat(result.sessionStatus()).isEqualTo("IN_PROGRESS");
        assertThat(result.navigation().getFirst().state()).isEqualTo("UNANSWERED");
        verify(mapper).startAnsweringSession(10L, 1L);
    }

    @Test
    void sessionNavigationSeparatesCorrectAndIncorrectSubmissions() {
        KnowledgePracticeNavigationRow correct = navigation(1, true, true);
        KnowledgePracticeNavigationRow incorrect = navigation(2, true, false);
        KnowledgePracticeNavigationRow unanswered = navigation(3, false, null);
        when(mapper.selectAnsweringSession(10L, 1L)).thenReturn(session("in_progress"));
        when(mapper.selectNavigation(10L)).thenReturn(List.of(correct, incorrect, unanswered));

        var result = service.session(1L, 10L);

        assertThat(result.navigation()).extracting(item -> item.state())
            .containsExactly("SUBMITTED_CORRECT", "SUBMITTED_INCORRECT", "UNANSWERED");
    }

    @Test
    void itemDoesNotExposeFeedbackBeforeSubmission() {
        when(mapper.selectAnsweringSession(10L, 1L)).thenReturn(session("in_progress"));
        when(mapper.selectAnsweringItem(10L, 1L, 1)).thenReturn(item());

        var result = service.item(1L, 10L, 1);

        assertThat(result.question().selectionMode()).isEqualTo("single");
        assertThat(result.submission()).isNull();
    }

    @Test
    void submitsGradesAndSettlesEveryFrozenLeaf() {
        KnowledgePracticeItemRow item = item();
        when(mapper.insertActionIdempotency(anyLong(), anyString(), anyString(), anyString())).thenReturn(1);
        when(mapper.lockAnsweringSession(10L, 1L)).thenReturn(session("in_progress"));
        when(mapper.selectAnsweringItem(10L, 1L, 1)).thenReturn(item);
        when(mapper.insertAttempt(anyLong(), anyLong(), anyLong(), anyString())).thenReturn(1);
        when(mapper.insertSingleSettlement(anyLong(), any(), anyLong(), anyLong(), anyBoolean(), anyString()))
            .thenReturn(99L);
        when(mapper.selectParentAggregates(1L, 20L)).thenReturn(List.of());
        when(mapper.countSubmittedItems(10L)).thenReturn(1);
        SubmitKnowledgePracticeItemBo command = new SubmitKnowledgePracticeItemBo();
        SubmitKnowledgePracticeItemBo.AnswerBo answer = new SubmitKnowledgePracticeItemBo.AnswerBo();
        answer.setValue(List.of("A")); command.setAnswer(answer);

        var result = service.submit(1L, 10L, 1, "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd", command);

        assertThat(result.feedback().correct()).isTrue();
        verify(mapper).insertPracticeEvidence(anyLong(), Mockito.eq(99L), Mockito.eq(item), anyLong(), Mockito.eq(30L), Mockito.eq(true));
        verify(mapper).aggregateSubjectProfiles(1L, 20L, 40L);
        verify(mapper).aggregateOverallProfile(1L, 20L, 40L);
    }

    @Test
    void rejectsMultipleAnswersBeforeCreatingIdempotencyRecord() {
        SubmitKnowledgePracticeItemBo command = command("A", "B");

        assertThatThrownBy(() -> service.submit(1L, 10L, 1,
            "0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd", command))
            .hasMessageContaining("单选题必须且只能选择一个有效选项");
    }

    @Test
    void completesCreatedSessionThroughAllInternalStates() {
        when(mapper.insertActionIdempotency(anyLong(), anyString(), anyString(), anyString())).thenReturn(1);
        when(mapper.lockAnsweringSession(10L, 1L)).thenReturn(session("created"));
        when(mapper.submitAnsweringSession(10L, 1L)).thenReturn(1);
        when(mapper.settleAnsweringSession(10L, 1L)).thenReturn(1);
        when(mapper.completeAnsweringSession(10L, 1L)).thenReturn(1);

        var result = service.complete(1L, 10L, "5d45fcea-bd6e-46e4-9230-230bb50daf32");

        assertThat(result.sessionStatus()).isEqualTo("COMPLETED");
        verify(mapper).submitAnsweringSession(10L, 1L);
        verify(mapper).settleAnsweringSession(10L, 1L);
        verify(mapper).completeAnsweringSession(10L, 1L);
    }

    private SubmitKnowledgePracticeItemBo command(String... values) {
        SubmitKnowledgePracticeItemBo command = new SubmitKnowledgePracticeItemBo();
        SubmitKnowledgePracticeItemBo.AnswerBo answer = new SubmitKnowledgePracticeItemBo.AnswerBo();
        answer.setValue(List.of(values));
        command.setAnswer(answer);
        return command;
    }

    private KnowledgePracticeAnsweringSessionRow session(String status) {
        KnowledgePracticeAnsweringSessionRow row = new KnowledgePracticeAnsweringSessionRow();
        row.setId(10L); row.setUserId(1L); row.setGoalId(20L); row.setRuleVersionId(40L);
        row.setStatus(status); row.setTotalCount(1); row.setSubmittedCount(0); return row;
    }

    private KnowledgePracticeNavigationRow navigation(int order, boolean submitted, Boolean correct) {
        KnowledgePracticeNavigationRow row = new KnowledgePracticeNavigationRow();
        row.setQuestionOrder(order); row.setSubmitted(submitted); row.setCorrect(correct);
        return row;
    }

    private KnowledgePracticeItemRow item() {
        KnowledgePracticeItemRow row = new KnowledgePracticeItemRow();
        row.setSessionQuestionId(11L); row.setSessionId(10L); row.setUserId(1L); row.setGoalId(20L);
        row.setRuleVersionId(40L); row.setExamSubjectId(50L); row.setEvidenceGroupKey("Q:1");
        row.setDifficultySnapshot("medium"); row.setQuestionOrder(1); row.setTotalCount(1); row.setSessionStatus("in_progress");
        row.setPresentationSnapshot("{\"stem\":\"s\",\"options\":[{\"label\":\"A\",\"content\":\"a\"}],\"images\":[]}");
        row.setGradingSnapshot("{\"answer\":{\"value\":[\"A\"]},\"analysis\":\"ok\"}");
        row.setKnowledgeSnapshot("{\"items\":[{\"knowledgePointId\":\"30\"}]}");
        return row;
    }
}
