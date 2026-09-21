package org.dromara.certmuse.question.service;

import org.dromara.certmuse.question.domain.QuestionRows;
import org.dromara.certmuse.question.domain.bo.QuestionQueryBo;
import org.dromara.certmuse.question.domain.bo.QuestionSaveBo;
import org.dromara.certmuse.question.domain.vo.QuestionReviewMutationVo;
import org.dromara.certmuse.question.mapper.CollectionMapper;
import org.dromara.certmuse.question.mapper.QuestionMapper;
import org.dromara.certmuse.question.service.impl.QuestionReviewSubmissionSupport;
import org.dromara.certmuse.question.service.impl.QuestionServiceImpl;
import org.dromara.certmuse.question.support.QuestionException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class QuestionServiceTest {

    @Mock
    private QuestionMapper mapper;
    @Mock
    private QuestionImageUrlService imageUrlService;
    @Mock
    private QuestionReviewSubmissionSupport reviewSubmissionSupport;
    @Mock
    private CollectionMapper collectionMapper;

    private QuestionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QuestionServiceImpl(mapper, JsonMapper.builder().build(), imageUrlService,
            reviewSubmissionSupport, collectionMapper);
    }

    @Test
    void listsQuestionsAfterApplyingScopeFilters() {
        QuestionRows.ListItem row = new QuestionRows.ListItem();
        row.setQuestionId("10");
        row.setRevisionId("11");
        row.setRevisionNo(2);
        row.setQuestionCode("Q-10");
        row.setSyllabusVersionId("2");
        row.setSyllabusVersionName("考纲");
        row.setStemSummary("题干");
        row.setExamSubjectId("3");
        row.setExamSubjectName("综合知识");
        row.setQuestionType("CHOICE");
        row.setDifficulty("easy");
        row.setStatus("draft");
        row.setHasReviewOpinion(true);
        row.setUpdatedTime(OffsetDateTime.parse("2026-08-10T09:00:00+08:00"));
        when(mapper.selectQuestions(any(), eq(null), eq(null), eq(null), eq(null), eq(7L), eq(20), eq(0L)))
            .thenReturn(List.of(row));
        when(mapper.countQuestions(any(), eq(null), eq(null), eq(null), eq(null), eq(7L))).thenReturn(1L);

        try (MockedStatic<LoginHelper> login = loginAs(7L)) {
            var result = service.list(new QuestionQueryBo());

            assertThat(result.getTotal()).isEqualTo(1L);
            assertThat(result.getRows()).singleElement().satisfies(item -> {
                assertThat(item.questionId()).isEqualTo("10");
                assertThat(item.status()).isEqualTo("draft");
                assertThat(item.hasReviewOpinion()).isTrue();
            });
        }
    }

    @Test
    void assemblesQuestionDetailsAndPreviewIncludingImagesAndScoringPoints() {
        QuestionRows.Revision revision = revision(10L, 11L, "draft");
        QuestionRows.IdLabel syllabus = new QuestionRows.IdLabel();
        syllabus.setId("2");
        syllabus.setLabel("考纲");
        QuestionRows.IdLabel subject = new QuestionRows.IdLabel();
        subject.setId("3");
        subject.setLabel("综合知识");
        QuestionRows.Option option = new QuestionRows.Option();
        option.setLabel("A");
        option.setContent("选项A");
        option.setSortOrder(1);
        QuestionRows.Image image = new QuestionRows.Image();
        image.setId(31L);
        image.setSortOrder(1);
        image.setSourceUrl("https://example.test/a.png");
        image.setAlt("示例");
        when(mapper.selectRevision(10L, null, 7L)).thenReturn(revision);
        when(mapper.selectKnowledge(11L)).thenReturn(List.of(knowledge(100L)));
        when(mapper.selectSyllabusLabel(2L)).thenReturn(syllabus);
        when(mapper.selectExamSubjectOptions(1L)).thenReturn(List.of(subject));
        when(mapper.selectImages(11L)).thenReturn(List.of(image));
        when(mapper.selectOptions(11L)).thenReturn(List.of(option));
        when(imageUrlService.accessUrl("https://example.test/a.png", null)).thenReturn("https://example.test/a.png");

        try (MockedStatic<LoginHelper> login = loginAs(7L)) {
            var detail = service.detail("10", null);
            var preview = service.preview("10", null);

            assertThat(detail.editableMode()).isEqualTo("update_working_revision");
            assertThat(detail.knowledgeBindings()).singleElement().satisfies(binding ->
                assertThat(binding.knowledgePointId()).isEqualTo("100"));
            assertThat(detail.images()).singleElement().satisfies(item ->
                assertThat(item.url()).isEqualTo("https://example.test/a.png"));
            assertThat(preview.questionId()).isEqualTo("10");
            assertThat(preview.options()).singleElement().satisfies(item ->
                assertThat(item.label()).isEqualTo("A"));
        }
    }

    @Test
    void assemblesQualificationScopedQuestionDetailWithoutSyllabus() {
        QuestionRows.Revision revision = revision(10L, 11L, "rejected");
        when(mapper.selectRevision(10L, null, 7L)).thenReturn(revision);
        when(mapper.selectKnowledge(11L)).thenReturn(List.of());
        when(mapper.selectExamSubjectOptions(1L)).thenReturn(List.of());
        when(mapper.selectImages(11L)).thenReturn(List.of());
        when(mapper.selectOptions(11L)).thenReturn(List.of());

        try (MockedStatic<LoginHelper> login = loginAs(7L)) {
            var detail = service.detail("10", null);

            assertThat(detail.syllabusVersionId()).isNull();
            assertThat(detail.syllabusVersionName()).isNull();
            assertThat(detail.status()).isEqualTo("rejected");
            assertThat(detail.knowledgeBindings()).isEmpty();
        }

        verify(mapper, never()).selectSyllabusLabel(anyLong());
    }

    @Test
    void savesAChoiceDraftAndReplacesItsChildren() {
        String requestId = UUID.randomUUID().toString();
        QuestionRows.Revision source = revision(10L, 11L, "draft");
        QuestionSaveBo command = choiceCommand();
        when(mapper.selectIdempotency("save_question_draft", requestId)).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("save_question_draft"), eq(requestId), anyString(),
            eq("question"), eq(10L), any())).thenReturn(1);
        when(mapper.lockRevision(10L, 11L, 7L)).thenReturn(source);
        when(mapper.selectKnowledge(11L)).thenReturn(List.of(knowledge(100L)));
        when(mapper.selectKnowledgeMetadata(any())).thenReturn(List.of(knowledge(100L)));
        when(mapper.updateRevision(eq(11L), eq(0L), eq("draft"), eq("CHOICE"), eq("medium"), eq(30),
            eq("新题干"), anyString(), isNull(), isNull(), anyString(), anyString(), eq(7L))).thenReturn(1);

        try (MockedStatic<LoginHelper> login = loginAs(7L)) {
            var result = service.save("10", requestId, command);

            assertThat(result.questionId()).isEqualTo("10");
            assertThat(result.status()).isEqualTo("draft");
            assertThat(result.rowVersion()).isEqualTo("1");
        }

        verify(mapper).insertOption(anyLong(), eq(11L), eq("A"), eq("选项A"), eq(1), eq(7L));
        verify(mapper).insertOption(anyLong(), eq(11L), eq("B"), eq("选项B"), eq(2), eq(7L));
        verify(mapper).completeIdempotency(anyLong(), eq(200), anyString());
    }

    @Test
    void savesARejectedSubjectiveDraftWithoutScoringPoints() {
        String requestId = UUID.randomUUID().toString();
        QuestionRows.Revision source = revision(10L, 11L, "rejected");
        QuestionSaveBo command = subjectiveCommand();
        when(mapper.selectIdempotency("save_question_draft", requestId)).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("save_question_draft"), eq(requestId), anyString(),
            eq("question"), eq(10L), any())).thenReturn(1);
        when(mapper.lockRevision(10L, 11L, 7L)).thenReturn(source);
        when(mapper.selectKnowledge(11L)).thenReturn(List.of(knowledge(100L)));
        when(mapper.selectKnowledgeMetadata(any())).thenReturn(List.of(knowledge(100L)));
        when(mapper.updateRevision(anyLong(), anyLong(), eq("rejected"), eq("CASE"), any(), any(),
            eq("新题干"), anyString(), any(), any(), anyString(), anyString(), eq(7L))).thenReturn(1);

        try (MockedStatic<LoginHelper> login = loginAs(7L)) {
            assertThat(service.save("10", requestId, command).status()).isEqualTo("rejected");
        }

        verify(mapper, never()).insertRevisionEvent(anyLong(), eq(10L), eq(11L), eq("reopened"),
            eq("rejected"), eq("draft"), isNull(), eq(7L), eq(requestId), anyString());
    }

    @Test
    void rejectsPublishedEditsStaleVersionsReferencedDeletesAndInvalidReviewStates() {
        String publishedRequest = UUID.randomUUID().toString();
        QuestionRows.Revision published = revision(10L, 11L, "published");
        when(mapper.selectIdempotency("save_question_draft", publishedRequest)).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("save_question_draft"), eq(publishedRequest), anyString(),
            eq("question"), eq(10L), any())).thenReturn(1);
        when(mapper.lockRevision(10L, 11L, 7L)).thenReturn(published);

        try (MockedStatic<LoginHelper> login = loginAs(7L)) {
            assertThatThrownBy(() -> service.save("10", publishedRequest, choiceCommand()))
                .isInstanceOfSatisfying(QuestionException.class, exception ->
                    assertThat(exception.getData().errorCode()).isEqualTo("QUESTION_STATUS_NOT_EDITABLE"));
        }

        String staleRequest = UUID.randomUUID().toString();
        QuestionRows.Revision stale = revision(10L, 11L, "draft");
        stale.setRowVersion(2L);
        when(mapper.selectIdempotency("save_question_draft", staleRequest)).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("save_question_draft"), eq(staleRequest), anyString(),
            eq("question"), eq(10L), any())).thenReturn(1);
        when(mapper.lockRevision(10L, 11L, 7L)).thenReturn(stale);

        try (MockedStatic<LoginHelper> login = loginAs(7L)) {
            assertThatThrownBy(() -> service.save("10", staleRequest, choiceCommand()))
                .isInstanceOfSatisfying(QuestionException.class, exception ->
                    assertThat(exception.getData().errorCode()).isEqualTo("QUESTION_VERSION_CONFLICT"));
        }

        String deleteRequest = UUID.randomUUID().toString();
        QuestionRows.Revision draft = revision(10L, 11L, "draft");
        when(mapper.selectIdempotency("delete_question", deleteRequest)).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("delete_question"), eq(deleteRequest), anyString(),
            eq("question"), eq(10L), any())).thenReturn(1);
        when(mapper.selectRevision(10L, null, 7L)).thenReturn(draft);
        when(mapper.countPublishedHistory(10L)).thenReturn(0);
        when(mapper.countQuestionReferences(10L)).thenReturn(1);

        try (MockedStatic<LoginHelper> login = loginAs(7L)) {
            assertThatThrownBy(() -> service.delete("10", deleteRequest))
                .isInstanceOfSatisfying(QuestionException.class, exception ->
                    assertThat(exception.getData().errorCode()).isEqualTo("QUESTION_DELETE_REFERENCED"));
        }

        when(mapper.lockReviewRevision(11L, 7L)).thenReturn(published);
        try (MockedStatic<LoginHelper> login = loginAs(7L)) {
            assertThatThrownBy(() -> service.submitReview("11", UUID.randomUUID().toString()))
                .isInstanceOfSatisfying(QuestionException.class, exception ->
                    assertThat(exception.getData().errorCode()).isEqualTo("QUESTION_REVIEW_INVALID_STATUS"));
        }
    }

    @Test
    void deletesAnUnpublishedUnreferencedDraft() {
        String requestId = UUID.randomUUID().toString();
        QuestionRows.Revision draft = revision(10L, 11L, "draft");
        when(mapper.selectIdempotency("delete_question", requestId)).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("delete_question"), eq(requestId), anyString(),
            eq("question"), eq(10L), any())).thenReturn(1);
        when(mapper.selectRevision(10L, null, 7L)).thenReturn(draft);
        when(mapper.countPublishedHistory(10L)).thenReturn(0);
        when(mapper.countQuestionReferences(10L)).thenReturn(0);
        when(mapper.softDeleteQuestion(10L, 7L)).thenReturn(1);

        try (MockedStatic<LoginHelper> login = loginAs(7L)) {
            service.delete("10", requestId);
        }

        verify(mapper).insertAudit(anyLong(), eq(7L), eq(10L), anyString(), anyString(), anyString());
        verify(mapper).completeIdempotency(anyLong(), eq(200), anyString());
    }

    @Test
    void submitsAValidDraftForReview() {
        String requestId = UUID.randomUUID().toString();
        QuestionRows.Revision draft = revision(10L, 11L, "draft");
        when(mapper.lockReviewRevision(11L, 7L)).thenReturn(draft);
        when(mapper.selectIdempotency("submit_review", requestId)).thenReturn(null);
        when(reviewSubmissionSupport.validate(draft)).thenReturn(List.of());
        when(mapper.insertIdempotency(anyLong(), eq("submit_review"), eq(requestId), anyString(),
            eq("question_revision"), eq(11L), any())).thenReturn(1);

        try (MockedStatic<LoginHelper> login = loginAs(7L)) {
            var result = service.submitReview("11", requestId);

            assertThat(result.status()).isEqualTo("pending_review");
            assertThat(result.rowVersion()).isEqualTo("1");
        }

        verify(reviewSubmissionSupport).submitDraft(eq(draft), eq(7L), eq(requestId), anyString());
        verify(mapper).completeIdempotency(anyLong(), eq(200), anyString());
    }

    @Test
    void approvesRejectsAndOfflinesReviewRevisions() {
        String approveRequest = UUID.randomUUID().toString();
        QuestionRows.Revision pending = revision(10L, 11L, "pending_review");
        when(mapper.selectReviewIdempotency(approveRequest)).thenReturn(null);
        when(mapper.lockReviewRevision(11L, 7L)).thenReturn(pending);
        when(mapper.insertReviewIdempotency(anyLong(), eq("approve_question_revision"), eq(approveRequest), anyString(),
            eq(10L), any())).thenReturn(1);
        when(mapper.markReviewPublished(11L, 7L)).thenReturn(1);

        try (MockedStatic<LoginHelper> login = loginAs(7L)) {
            QuestionReviewMutationVo approved = service.approve("11", approveRequest);
            assertThat(approved.status()).isEqualTo("published");
        }

        String rejectRequest = UUID.randomUUID().toString();
        when(mapper.selectReviewIdempotency(rejectRequest)).thenReturn(null);
        when(mapper.insertReviewIdempotency(anyLong(), eq("reject_question_revision"), eq(rejectRequest), anyString(),
            eq(10L), any())).thenReturn(1);
        when(mapper.markReviewRejected(11L, 7L, "依据不足")).thenReturn(1);
        when(collectionMapper.lockPendingRevisionsByQuestionRevision(11L)).thenReturn(List.of());

        try (MockedStatic<LoginHelper> login = loginAs(7L)) {
            assertThat(service.reject("11", rejectRequest, "依据不足").status()).isEqualTo("rejected");
        }

        String offlineRequest = UUID.randomUUID().toString();
        QuestionRows.Revision published = revision(10L, 11L, "published");
        when(mapper.selectReviewIdempotency(offlineRequest)).thenReturn(null);
        when(mapper.lockReviewRevision(11L, 7L)).thenReturn(published);
        when(mapper.insertReviewIdempotency(anyLong(), eq("offline_question_revision"), eq(offlineRequest), anyString(),
            eq(10L), any())).thenReturn(1);
        when(mapper.markPublishedDraft(11L, 7L)).thenReturn(1);

        try (MockedStatic<LoginHelper> login = loginAs(7L)) {
            assertThat(service.takeOffline("11", offlineRequest).status()).isEqualTo("draft");
        }

        verify(mapper, org.mockito.Mockito.times(3)).completeIdempotency(anyLong(), eq(200), anyString());
    }

    @Test
    void rejectsInvalidQuestionIdentifiersBeforeTouchingPersistence() {
        assertThatThrownBy(() -> service.detail("0", null))
            .isInstanceOfSatisfying(QuestionException.class, exception ->
                assertThat(exception.getData().errorCode()).isEqualTo("QUESTION_QUERY_INVALID"));
        verify(mapper, never()).selectRevision(anyLong(), any(), any());
    }

    private static MockedStatic<LoginHelper> loginAs(long userId) {
        MockedStatic<LoginHelper> login = org.mockito.Mockito.mockStatic(LoginHelper.class);
        login.when(LoginHelper::isSuperAdmin).thenReturn(false);
        login.when(LoginHelper::getUserId).thenReturn(userId);
        return login;
    }

    private static QuestionRows.Revision revision(long questionId, long revisionId, String status) {
        QuestionRows.Revision row = new QuestionRows.Revision();
        row.setQuestionId(questionId);
        row.setRevisionId(revisionId);
        row.setRevisionNo(1);
        row.setRowVersion(0L);
        row.setQuestionCode("Q-10");
        row.setExamSubjectId(3L);
        row.setExamSubjectName("综合知识");
        row.setCertificationId(1L);
        row.setCertificationName("系统架构设计师");
        row.setQuestionType("CHOICE");
        row.setDifficulty("easy");
        row.setEstimatedSeconds(60);
        row.setStem("原题干");
        row.setAnswer("{\"schema_version\":\"1.0\",\"answer_type\":\"option_keys\",\"selection_mode\":\"single\",\"value\":[\"A\"]}");
        row.setAnalysis("解析");
        row.setCommonMistakes("易错点");
        row.setStatus(status);
        row.setUpdatedTime(OffsetDateTime.parse("2026-08-10T09:00:00+08:00"));
        return row;
    }

    private static QuestionRows.Knowledge knowledge(long id) {
        QuestionRows.Knowledge row = new QuestionRows.Knowledge();
        row.setKnowledgePointId(id);
        row.setKnowledgePointLabel("知识点");
        row.setRelationRole("primary");
        row.setSortOrder(0);
        row.setSyllabusVersionId(2L);
        row.setExamSubjectId(3L);
        row.setLeaf(true);
        return row;
    }

    private static QuestionSaveBo choiceCommand() {
        QuestionSaveBo command = baseCommand("CHOICE");
        QuestionSaveBo.OptionBo first = option("A", "选项A", 1);
        QuestionSaveBo.OptionBo second = option("B", "选项B", 2);
        command.setOptions(List.of(first, second));
        QuestionSaveBo.AnswerBo answer = new QuestionSaveBo.AnswerBo();
        answer.setSchemaVersion("1.0");
        answer.setAnswerType("option_keys");
        answer.setSelectionMode("single");
        answer.setValue(List.of("A"));
        command.setAnswer(answer);
        return command;
    }

    private static QuestionSaveBo subjectiveCommand() {
        QuestionSaveBo command = baseCommand("CASE");
        QuestionSaveBo.AnswerBo answer = new QuestionSaveBo.AnswerBo();
        answer.setSchemaVersion("1.0");
        answer.setAnswerType("reference_text");
        answer.setValue("参考答案");
        command.setAnswer(answer);
        return command;
    }

    private static QuestionSaveBo baseCommand(String type) {
        QuestionSaveBo command = new QuestionSaveBo();
        command.setBaseRevisionId("11");
        command.setRowVersion("0");
        command.setExamSubjectId("3");
        command.setQuestionType(type);
        command.setDifficulty("medium");
        command.setEstimatedSeconds(30);
        command.setStem(" 新题干 ");
        QuestionSaveBo.KnowledgeBindingBo binding = new QuestionSaveBo.KnowledgeBindingBo();
        binding.setKnowledgePointId("100");
        binding.setRelationRole("primary");
        binding.setSortOrder(0);
        command.setKnowledgeBindings(List.of(binding));
        return command;
    }

    private static QuestionSaveBo.OptionBo option(String label, String content, int order) {
        QuestionSaveBo.OptionBo option = new QuestionSaveBo.OptionBo();
        option.setLabel(label);
        option.setContent(content);
        option.setSortOrder(order);
        return option;
    }
}
