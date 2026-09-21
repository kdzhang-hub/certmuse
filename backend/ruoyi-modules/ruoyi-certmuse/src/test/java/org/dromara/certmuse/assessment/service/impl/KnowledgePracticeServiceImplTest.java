package org.dromara.certmuse.assessment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeGoalRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeNodeRow;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeQuestionRow;
import org.dromara.certmuse.assessment.domain.bo.StartKnowledgePracticeBo;
import org.dromara.certmuse.assessment.mapper.KnowledgePracticeMapper;
import org.dromara.certmuse.assessment.service.ChoiceAnswerSettlementService;
import org.dromara.certmuse.assessment.support.KnowledgePracticeException;
import org.dromara.certmuse.question.service.QuestionImageUrlService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

@Tag("dev")
class KnowledgePracticeServiceImplTest {
    private final KnowledgePracticeMapper mapper = Mockito.mock(KnowledgePracticeMapper.class);
    private final QuestionImageUrlService imageUrlService = Mockito.mock(QuestionImageUrlService.class);
    private final KnowledgePracticeServiceImpl service = new KnowledgePracticeServiceImpl(
        mapper, JsonMapper.builder().build(), imageUrlService,
        new ChoiceAnswerSettlementService(mapper,
            Mockito.mock(org.dromara.certmuse.learning.service.MistakeFactRecorder.class), JsonMapper.builder().build()));

    @Test
    void setupReturnsUnassessedVisibleTreeWithoutTruncatingCount() {
        KnowledgePracticeGoalRow goal = goal();
        KnowledgePracticeNodeRow node = node();
        node.setQuestionCount(151L);
        node.setProfileStatus("unassessed");
        node.setConfidenceLevel("unassessed");
        when(mapper.selectCurrentGoal(1L)).thenReturn(goal);
        when(mapper.selectVisibleNodes(1L, goal.getId(), goal.getSyllabusVersionId())).thenReturn(List.of(node));

        var result = service.setup(1L);

        assertThat(result.goal().version()).isEqualTo(4);
        assertThat(result.subjects().getFirst().nodes().getFirst().questionCount()).isEqualTo(151);
        assertThat(result.subjects().getFirst().nodes().getFirst().mastery().currentDirectAbility()).isNull();
    }

    @Test
    void goalGatePrecedesOnboarding() {
        KnowledgePracticeGoalRow goal = goal();
        goal.setGoalStatus("paused");
        goal.setOnboardingComplete(false);
        when(mapper.selectCurrentGoal(1L)).thenReturn(goal);

        assertCode(() -> service.setup(1L), "LEARNING_NOT_ALLOWED");
    }

    @Test
    void setupDoesNotRequireInitialDiagnosis() {
        KnowledgePracticeGoalRow goal = goal();
        goal.setOnboardingComplete(false);
        when(mapper.selectCurrentGoal(1L)).thenReturn(goal);
        when(mapper.selectVisibleNodes(1L, goal.getId(), goal.getSyllabusVersionId())).thenReturn(List.of(node()));

        var result = service.setup(1L);

        assertThat(result.subjects()).hasSize(1);
    }

    @Test
    void rejectsMoreThanOneHundredFiftyQuestionsOnlyOnStart() {
        KnowledgePracticeGoalRow goal = goal();
        when(mapper.selectCurrentGoal(1L)).thenReturn(goal);
        when(mapper.selectPracticeNode(1L, goal.getId(), goal.getSyllabusVersionId(), 30L)).thenReturn(node(2));
        List<KnowledgePracticeQuestionRow> rows = new ArrayList<>();
        for (int index = 0; index < 151; index++) rows.add(question(index + 1L));
        when(mapper.selectQualifiedQuestions(goal.getSyllabusVersionId(), 30L)).thenReturn(rows);

        assertCode(() -> service.start(1L, "0F7CD9A8-4B29-4BA5-B202-B7FB5AAC85DD", command()),
            "PRACTICE_QUESTION_LIMIT_EXCEEDED");
    }

    @Test
    void createsSessionSnapshotsAndCompletesIdempotency() {
        KnowledgePracticeGoalRow goal = goal();
        when(mapper.selectCurrentGoal(1L)).thenReturn(goal);
        when(mapper.selectPracticeNode(1L, goal.getId(), goal.getSyllabusVersionId(), 30L)).thenReturn(node(2));
        when(mapper.selectQualifiedQuestions(goal.getSyllabusVersionId(), 30L)).thenReturn(List.of(question(50L)));
        when(mapper.selectPublishedRuleVersion()).thenReturn(60L);
        when(mapper.insertIdempotency(anyLong(), anyString(), anyString())).thenReturn(1);
        when(mapper.insertSession(anyLong(), anyLong(), any(), anyLong(), anyLong(), anyString())).thenReturn(1);

        var result = service.start(1L, "0F7CD9A8-4B29-4BA5-B202-B7FB5AAC85DD", command());

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.answerPath()).contains(result.sessionId());
        ArgumentCaptor<String> payloadHash = ArgumentCaptor.forClass(String.class);
        verify(mapper).insertIdempotency(anyLong(), anyString(), payloadHash.capture());
        assertThat(payloadHash.getValue())
            .isEqualTo("16d954f8daf98455377675f8d34102bea4401ea7fda19ef23d127df3385e43a7");
        verify(mapper).insertSessionQuestion(anyLong(), anyLong(), any(), Mockito.eq(1),
            Mockito.contains("knowledge_practice_presentation/1.0"),
            Mockito.contains("knowledge_practice_grading/1.0"),
            Mockito.contains("knowledge_practice_knowledge/1.0"));
        verify(mapper).completeIdempotency(anyLong(), anyLong(), Mockito.contains("\"code\":200"));
    }

    @Test
    void rejectsNonCanonicalUuid() {
        assertCode(() -> service.start(1L, " 0f7cd9a8-4b29-4ba5-b202-b7fb5aac85dd", command()),
            "PRACTICE_REQUEST_INVALID");
    }

    @Test
    void rejectsChapterWhenMapperDoesNotReturnItAsPracticeNode() {
        KnowledgePracticeGoalRow goal = goal();
        when(mapper.selectCurrentGoal(1L)).thenReturn(goal);
        when(mapper.selectPracticeNode(1L, goal.getId(), goal.getSyllabusVersionId(), 30L)).thenReturn(null);

        assertCode(() -> service.start(1L, "0F7CD9A8-4B29-4BA5-B202-B7FB5AAC85DD", command()),
            "PRACTICE_NODE_UNAVAILABLE");
    }

    @Test
    void startsVisibleKnowledgePointAtThirdLevel() {
        KnowledgePracticeGoalRow goal = goal();
        when(mapper.selectCurrentGoal(1L)).thenReturn(goal);
        when(mapper.selectPracticeNode(1L, goal.getId(), goal.getSyllabusVersionId(), 30L)).thenReturn(node(3));
        when(mapper.selectQualifiedQuestions(goal.getSyllabusVersionId(), 30L)).thenReturn(List.of(question(50L)));
        when(mapper.selectPublishedRuleVersion()).thenReturn(60L);
        when(mapper.insertIdempotency(anyLong(), anyString(), anyString())).thenReturn(1);
        when(mapper.insertSession(anyLong(), anyLong(), any(), anyLong(), anyLong(), anyString())).thenReturn(1);

        var result = service.start(1L, "1F7CD9A8-4B29-4BA5-B202-B7FB5AAC85DD", command());

        assertThat(result.totalCount()).isEqualTo(1);
        verify(mapper).selectQualifiedQuestions(goal.getSyllabusVersionId(), 30L);
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOf(KnowledgePracticeException.class)
            .satisfies(error -> assertThat(((KnowledgePracticeException) error).errorCode()).isEqualTo(code));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private KnowledgePracticeGoalRow goal() {
        KnowledgePracticeGoalRow goal = new KnowledgePracticeGoalRow();
        goal.setId(10L); goal.setCertificationId(11L); goal.setCertificationName("系统架构设计师");
        goal.setSyllabusVersionId(12L); goal.setSyllabusVersionName("2026 年考试大纲");
        goal.setRowVersion(4L); goal.setGoalStatus("active"); goal.setCertificationStatus("0");
        goal.setTargetExamYear(2026); goal.setTargetExamMonth(11); goal.setOnboardingComplete(true);
        return goal;
    }

    private KnowledgePracticeNodeRow node() {
        return node(1);
    }

    private KnowledgePracticeNodeRow node(int treeDepth) {
        KnowledgePracticeNodeRow node = new KnowledgePracticeNodeRow();
        node.setId(30L); node.setExamSubjectId(20L); node.setSubjectName("综合知识"); node.setSubjectOrder(1);
        node.setSyllabusNumber("1"); node.setSyllabusTitle("基础"); node.setTreeDepth(treeDepth); node.setSortOrder(1);
        node.setImportance(3); node.setQuestionCount(1L); node.setProfileStatus("unassessed");
        node.setConfidenceLevel("unassessed"); return node;
    }

    private KnowledgePracticeQuestionRow question(long id) {
        KnowledgePracticeQuestionRow row = new KnowledgePracticeQuestionRow();
        row.setQuestionId(id); row.setRevisionId(id + 100); row.setExamSubjectId(20L);
        row.setEvidenceGroupKey("Q:" + id); row.setQuestionType("CHOICE"); row.setDifficulty("medium");
        row.setEstimatedSeconds(60); row.setStem("redacted-test-stem");
        row.setAnswerJson("{\"schema_version\":\"1.0\",\"value\":[\"A\"]}");
        row.setOptionsJson("[{\"label\":\"A\",\"content\":\"option\",\"sortOrder\":1}]");
        row.setImagesJson("[]"); row.setKnowledgeJson("[]");
        row.setGradingRulesJson("{}"); row.setMaxScore(BigDecimal.ONE); return row;
    }

    private StartKnowledgePracticeBo command() {
        StartKnowledgePracticeBo command = new StartKnowledgePracticeBo();
        command.setKnowledgePointId("30"); command.setExpectedGoalVersion(4L); return command;
    }
}
