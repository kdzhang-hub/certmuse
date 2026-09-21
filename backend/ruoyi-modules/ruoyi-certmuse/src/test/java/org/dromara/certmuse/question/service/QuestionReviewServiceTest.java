package org.dromara.certmuse.question.service;

import cn.hutool.crypto.digest.DigestUtil;
import org.dromara.certmuse.question.domain.QuestionRows;
import org.dromara.certmuse.question.domain.CollectionRevisionRow;
import org.dromara.certmuse.question.domain.vo.QuestionErrorVo;
import org.dromara.certmuse.question.mapper.QuestionMapper;
import org.dromara.certmuse.question.mapper.CollectionMapper;
import org.dromara.certmuse.question.service.impl.QuestionReviewSubmissionSupport;
import org.dromara.certmuse.question.service.impl.QuestionServiceImpl;
import org.dromara.certmuse.question.support.QuestionException;
import org.dromara.certmuse.question.validation.QuestionSubmitReviewValidator;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class QuestionReviewServiceTest {

    private static final String REQUEST_ID = "00000000-0000-0000-0000-000000000001";

    @Mock
    private QuestionMapper mapper;
    @Mock
    private QuestionImageUrlService imageUrlService;
    @Mock
    private CollectionMapper collectionMapper;

    private QuestionService service;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        service = new QuestionServiceImpl(mapper, jsonMapper, imageUrlService,
            new QuestionReviewSubmissionSupport(mapper, new QuestionSubmitReviewValidator(jsonMapper), jsonMapper),
            collectionMapper);
    }

    @Test
    void approvesPendingRevisionPublishesItAndWritesReviewTrail() {
        QuestionRows.Revision revision = revision("pending_review", 4L);
        when(mapper.selectReviewIdempotency(REQUEST_ID)).thenReturn(null);
        when(mapper.lockReviewRevision(200L, 7L)).thenReturn(revision);
        when(mapper.insertReviewIdempotency(anyLong(), eq("approve_question_revision"), eq(REQUEST_ID),
            any(), eq(100L), any())).thenReturn(1);
        when(mapper.markReviewPublished(200L, 7L)).thenReturn(1);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            var result = service.approve("200", REQUEST_ID);

            assertThat(result.questionId()).isEqualTo("100");
            assertThat(result.revisionId()).isEqualTo("200");
            assertThat(result.status()).isEqualTo("published");
            assertThat(result.rowVersion()).isEqualTo("5");
        }

        verify(mapper).markReviewPublished(200L, 7L);
        verify(mapper).insertRevisionEvent(anyLong(), eq(100L), eq(200L), eq("review_approved"),
            eq("pending_review"), eq("published"), eq(null), eq(7L), eq(REQUEST_ID), any());
        verify(mapper).insertReviewAudit(anyLong(), eq(7L), eq("review_approve"), eq(200L), any(), any(), any());
        verify(mapper).completeIdempotency(anyLong(), eq(200), any());
    }

    @Test
    void rejectsPendingRevisionAfterTrimmingOpinion() {
        QuestionRows.Revision revision = revision("pending_review", 8L);
        when(mapper.selectReviewIdempotency(REQUEST_ID)).thenReturn(null);
        when(mapper.lockReviewRevision(200L, 7L)).thenReturn(revision);
        when(mapper.insertReviewIdempotency(anyLong(), eq("reject_question_revision"), eq(REQUEST_ID),
            any(), eq(100L), any())).thenReturn(1);
        when(mapper.markReviewRejected(200L, 7L, "依据不足")).thenReturn(1);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            var result = service.reject("200", REQUEST_ID, "  依据不足  ");

            assertThat(result.status()).isEqualTo("rejected");
            assertThat(result.rowVersion()).isEqualTo("9");
        }

        verify(mapper).markReviewRejected(200L, 7L, "依据不足");
        verify(mapper).insertRevisionEvent(anyLong(), eq(100L), eq(200L), eq("review_rejected"),
            eq("pending_review"), eq("rejected"), eq("依据不足"), eq(7L), eq(REQUEST_ID), any());
        verify(mapper).insertReviewAudit(anyLong(), eq(7L), eq("review_reject"), eq(200L), any(), any(), any());
    }

    @Test
    void rejectingQuestionCascadesEveryPendingCollectionAndWritesAudit() {
        QuestionRows.Revision revision = revision("pending_review", 8L);
        revision.setQuestionCode("Q-001");
        CollectionRevisionRow first = collectionRevision(301L, 401L, 2L);
        CollectionRevisionRow second = collectionRevision(302L, 402L, 4L);
        when(mapper.selectReviewIdempotency(REQUEST_ID)).thenReturn(null);
        when(mapper.lockReviewRevision(200L, 7L)).thenReturn(revision);
        when(mapper.insertReviewIdempotency(anyLong(), eq("reject_question_revision"), eq(REQUEST_ID),
            any(), eq(100L), any())).thenReturn(1);
        when(mapper.markReviewRejected(200L, 7L, "依据不足")).thenReturn(1);
        when(collectionMapper.lockPendingRevisionsByQuestionRevision(200L)).thenReturn(java.util.List.of(first, second));
        when(collectionMapper.markRejected(eq(301L), eq(7L), any())).thenReturn(1);
        when(collectionMapper.markRejected(eq(302L), eq(7L), any())).thenReturn(1);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);
            service.reject("200", REQUEST_ID, "依据不足");
        }

        verify(collectionMapper).markRejected(eq(301L), eq(7L),
            eq("题目Q-001（修订200）审核被驳回：依据不足"));
        verify(collectionMapper).markRejected(eq(302L), eq(7L),
            eq("题目Q-001（修订200）审核被驳回：依据不足"));
        verify(collectionMapper, org.mockito.Mockito.times(2)).insertReviewAudit(anyLong(), eq(7L),
            eq("reject_collection_by_question"), anyLong(), any(), any(), any());
    }

    @Test
    void takesPublishedRevisionOfflineAndReturnsItToDraft() {
        QuestionRows.Revision revision = revision("published", 9L);
        when(mapper.selectReviewIdempotency(REQUEST_ID)).thenReturn(null);
        when(mapper.lockReviewRevision(200L, 7L)).thenReturn(revision);
        when(mapper.insertReviewIdempotency(anyLong(), eq("offline_question_revision"), eq(REQUEST_ID),
            any(), eq(100L), any())).thenReturn(1);
        when(mapper.markPublishedDraft(200L, 7L)).thenReturn(1);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            var result = service.takeOffline("200", REQUEST_ID);

            assertThat(result.status()).isEqualTo("draft");
            assertThat(result.rowVersion()).isEqualTo("10");
        }

        verify(mapper).markPublishedDraft(200L, 7L);
        verify(mapper).insertRevisionEvent(anyLong(), eq(100L), eq(200L), eq("question_offline"),
            eq("published"), eq("draft"), eq(null), eq(7L), eq(REQUEST_ID), any());
        verify(mapper).insertReviewAudit(anyLong(), eq(7L), eq("question_offline"), eq(200L), any(), any(), any());
    }

    @Test
    void refusesToTakeNonPublishedRevisionOffline() {
        when(mapper.selectReviewIdempotency(REQUEST_ID)).thenReturn(null);
        when(mapper.lockReviewRevision(200L, 7L)).thenReturn(revision("draft", 2L));

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            assertThatThrownBy(() -> service.takeOffline("200", REQUEST_ID))
                .isInstanceOf(QuestionException.class)
                .extracting(error -> ((QuestionException) error).getErrorCode())
                .isEqualTo("QUESTION_OFFLINE_INVALID_STATUS");
        }
    }

    @Test
    void rejectsBlankOrOverlongOpinionWithTheDocumentedFieldError() {
        assertThatThrownBy(() -> service.reject("200", REQUEST_ID, null))
            .isInstanceOf(QuestionException.class)
            .extracting(error -> ((QuestionException) error).getErrorCode())
            .isEqualTo("REVIEW_OPINION_REQUIRED");
        assertThatThrownBy(() -> service.reject("200", REQUEST_ID, "  "))
            .isInstanceOf(QuestionException.class)
            .extracting(error -> ((QuestionException) error).getErrorCode())
            .isEqualTo("REVIEW_OPINION_REQUIRED");
        assertThatThrownBy(() -> service.reject("200", REQUEST_ID, "x".repeat(501)))
            .isInstanceOf(QuestionException.class)
            .extracting(error -> ((QuestionException) error).getErrorCode())
            .isEqualTo("REVIEW_OPINION_REQUIRED");
    }

    @Test
    void rejectsRequestIdReusedByAnotherReviewRequest() {
        QuestionRows.Idempotency existing = new QuestionRows.Idempotency();
        existing.setPayloadHash("different-payload");
        existing.setStatus("succeeded");
        when(mapper.selectReviewIdempotency(REQUEST_ID)).thenReturn(existing);

        assertThatThrownBy(() -> service.approve("200", REQUEST_ID))
            .isInstanceOf(QuestionException.class)
            .extracting(error -> ((QuestionException) error).getErrorCode())
            .isEqualTo("QUESTION_REVIEW_REQUEST_CONFLICT");
    }

    @Test
    void replaysCompletedRequestFoundAfterWaitingForTheRevisionLock() throws Exception {
        QuestionRows.Idempotency completed = new QuestionRows.Idempotency();
        completed.setPayloadHash(DigestUtil.sha256Hex("approve_question_revision\n200\n"));
        completed.setStatus("succeeded");
        completed.setResponseBody(JsonMapper.builder().build().writeValueAsString(java.util.Map.of(
            "response", java.util.Map.of("data", java.util.Map.of(
                "questionId", "100", "revisionId", "200", "status", "published", "rowVersion", "5"
            ))
        )));
        when(mapper.selectReviewIdempotency(REQUEST_ID)).thenReturn(null, completed);
        when(mapper.lockReviewRevision(200L, 7L)).thenReturn(revision("pending_review", 4L));

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            var result = service.approve("200", REQUEST_ID);

            assertThat(result.status()).isEqualTo("published");
            assertThat(result.rowVersion()).isEqualTo("5");
        }
    }

    @Test
    void submitsCompleteDraftAndWritesOnlySubmissionTrail() {
        QuestionRows.Revision revision = submitRevision("draft", 4L);
        QuestionRows.Option first = option("A", 1);
        QuestionRows.Option second = option("B", 2);
        QuestionRows.Knowledge knowledge = knowledge(10L, "primary", 0, true);
        when(mapper.lockReviewRevision(200L, 7L)).thenReturn(revision);
        when(mapper.selectIdempotency("submit_review", REQUEST_ID)).thenReturn(null);
        when(mapper.selectOptions(200L)).thenReturn(java.util.List.of(first, second));
        when(mapper.selectKnowledge(200L)).thenReturn(java.util.List.of(knowledge));
        when(mapper.selectKnowledgeMetadata(any())).thenReturn(java.util.List.of(knowledge));
        when(mapper.insertIdempotency(anyLong(), eq("submit_review"), eq(REQUEST_ID), any(),
            eq("question_revision"), eq(200L), any())).thenReturn(1);
        when(mapper.markReviewSubmitted(200L, 7L)).thenReturn(1);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            var result = service.submitReview("200", REQUEST_ID);

            assertThat(result.questionId()).isEqualTo("100");
            assertThat(result.revisionId()).isEqualTo("200");
            assertThat(result.status()).isEqualTo("pending_review");
            assertThat(result.rowVersion()).isEqualTo("5");
        }

        verify(mapper).insertRevisionEvent(anyLong(), eq(100L), eq(200L), eq("review_submitted"),
            eq("draft"), eq("pending_review"), eq(null), eq(7L), eq(REQUEST_ID), any());
        verify(mapper).insertReviewAudit(anyLong(), eq(7L), eq("review_submit"), eq(200L), any(), any(), any());
        verify(mapper).completeIdempotency(anyLong(), eq(200), any());
    }

    @Test
    void resubmitsRejectedRevisionAndRecordsItsActualPriorStatus() {
        QuestionRows.Revision revision = submitRevision("rejected", 4L);
        QuestionRows.Option first = option("A", 1);
        QuestionRows.Option second = option("B", 2);
        QuestionRows.Knowledge knowledge = knowledge(10L, "primary", 0, true);
        when(mapper.lockReviewRevision(200L, 7L)).thenReturn(revision);
        when(mapper.selectIdempotency("submit_review", REQUEST_ID)).thenReturn(null);
        when(mapper.selectOptions(200L)).thenReturn(java.util.List.of(first, second));
        when(mapper.selectKnowledge(200L)).thenReturn(java.util.List.of(knowledge));
        when(mapper.selectKnowledgeMetadata(any())).thenReturn(java.util.List.of(knowledge));
        when(mapper.insertIdempotency(anyLong(), eq("submit_review"), eq(REQUEST_ID), any(),
            eq("question_revision"), eq(200L), any())).thenReturn(1);
        when(mapper.markReviewSubmitted(200L, 7L)).thenReturn(1);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            assertThat(service.submitReview("200", REQUEST_ID).status()).isEqualTo("pending_review");
        }

        verify(mapper).insertRevisionEvent(anyLong(), eq(100L), eq(200L), eq("review_submitted"),
            eq("rejected"), eq("pending_review"), eq(null), eq(7L), eq(REQUEST_ID), any());
    }

    @Test
    void replaysCompletedSubmitRequestForTheSameRevision() throws Exception {
        QuestionRows.Idempotency completed = new QuestionRows.Idempotency();
        completed.setPayloadHash(DigestUtil.sha256Hex("submit_review\n200\n"));
        completed.setResourceId(200L);
        completed.setStatus("succeeded");
        completed.setResponseBody(JsonMapper.builder().build().writeValueAsString(java.util.Map.of(
            "response", java.util.Map.of("data", java.util.Map.of(
                "questionId", "100", "revisionId", "200", "status", "pending_review", "rowVersion", "5"
            ))
        )));
        when(mapper.lockReviewRevision(200L, 7L)).thenReturn(revision("pending_review", 5L));
        when(mapper.selectIdempotency("submit_review", REQUEST_ID)).thenReturn(completed);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            var result = service.submitReview("200", REQUEST_ID);

            assertThat(result.status()).isEqualTo("pending_review");
            assertThat(result.rowVersion()).isEqualTo("5");
        }

        verify(mapper, never()).markReviewSubmitted(anyLong(), any());
        verify(mapper, never()).insertRevisionEvent(anyLong(), anyLong(), anyLong(), eq("review_submitted"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsInvalidSubmitRequestIdBeforeLockingRevision() {
        assertThatThrownBy(() -> service.submitReview("200", "not-a-uuid"))
            .isInstanceOf(QuestionException.class)
            .extracting(error -> ((QuestionException) error).getErrorCode())
            .isEqualTo("QUESTION_QUERY_INVALID");

        verify(mapper, never()).lockReviewRevision(anyLong(), any());
    }

    @Test
    void keepsDraftUntouchedWhenPublishGateFails() {
        QuestionRows.Revision revision = submitRevision("draft", 4L);
        when(mapper.lockReviewRevision(200L, 7L)).thenReturn(revision);
        when(mapper.selectIdempotency("submit_review", REQUEST_ID)).thenReturn(null);
        when(mapper.selectOptions(200L)).thenReturn(java.util.List.of());
        when(mapper.selectKnowledge(200L)).thenReturn(java.util.List.of());

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            assertThatThrownBy(() -> service.submitReview("200", REQUEST_ID))
                .isInstanceOf(QuestionException.class)
                .satisfies(error -> {
                    QuestionException questionError = (QuestionException) error;
                    assertThat(questionError.getErrorCode()).isEqualTo("QUESTION_SUBMIT_REVIEW_CHECK_FAILED");
                    assertThat(questionError.getData().blockingIssues()).extracting(QuestionErrorVo.BlockingIssueVo::code)
                        .contains("QUESTION_CHOICE_OPTIONS_INVALID", "QUESTION_CHOICE_ANSWER_INVALID")
                        .doesNotContain("QUESTION_KNOWLEDGE_BINDINGS_REQUIRED");
                });
        }

        verify(mapper, never()).insertIdempotency(anyLong(), eq("submit_review"), any(), any(), any(), anyLong(), any());
        verify(mapper, never()).markReviewSubmitted(anyLong(), any());
        verify(mapper, never()).insertRevisionEvent(anyLong(), anyLong(), anyLong(), eq("review_submitted"), any(), any(), any(), any(), any(), any());
        verify(mapper, never()).insertReviewAudit(anyLong(), any(), eq("review_submit"), anyLong(), any(), any(), any());
    }

    private static QuestionRows.Revision revision(String status, long rowVersion) {
        QuestionRows.Revision revision = new QuestionRows.Revision();
        revision.setQuestionId(100L);
        revision.setRevisionId(200L);
        revision.setRowVersion(rowVersion);
        revision.setStatus(status);
        return revision;
    }

    private static QuestionRows.Revision submitRevision(String status, long rowVersion) {
        QuestionRows.Revision revision = revision(status, rowVersion);
        revision.setExamSubjectId(11L);
        revision.setQuestionType("CHOICE");
        revision.setStem("题干");
        revision.setAnswer("{\"schema_version\":\"1.0\",\"answer_type\":\"option_keys\",\"selection_mode\":\"single\",\"value\":[\"B\"]}");
        return revision;
    }

    private static QuestionRows.Option option(String label, int sortOrder) {
        QuestionRows.Option option = new QuestionRows.Option();
        option.setLabel(label); option.setSortOrder(sortOrder);
        return option;
    }

    private static QuestionRows.Knowledge knowledge(long id, String role, int sortOrder, boolean leaf) {
        QuestionRows.Knowledge knowledge = new QuestionRows.Knowledge();
        knowledge.setKnowledgePointId(id); knowledge.setRelationRole(role); knowledge.setSortOrder(sortOrder);
        knowledge.setLeaf(leaf); knowledge.setSyllabusVersionId(20L); knowledge.setExamSubjectId(11L);
        return knowledge;
    }

    private static CollectionRevisionRow collectionRevision(long revisionId, long collectionId, long rowVersion) {
        CollectionRevisionRow row = new CollectionRevisionRow();
        row.setId(revisionId);
        row.setCollectionId(collectionId);
        row.setRevisionNo(1);
        row.setRowVersion(rowVersion);
        row.setStatus("pending_review");
        return row;
    }
}
