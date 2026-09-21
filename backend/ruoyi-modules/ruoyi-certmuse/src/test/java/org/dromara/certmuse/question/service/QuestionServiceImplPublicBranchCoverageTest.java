package org.dromara.certmuse.question.service;

import cn.hutool.crypto.digest.DigestUtil;
import org.dromara.certmuse.question.domain.QuestionRows;
import org.dromara.certmuse.question.domain.bo.QuestionQueryBo;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class QuestionServiceImplPublicBranchCoverageTest {

    private static final long USER_ID = 7L;
    private static final long QUESTION_ID = 100L;
    private static final long REVISION_ID = 200L;

    @Mock
    private QuestionMapper mapper;
    @Mock
    private QuestionImageUrlService imageUrlService;
    @Mock
    private QuestionReviewSubmissionSupport reviewSubmissionSupport;
    @Mock
    private CollectionMapper collectionMapper;

    private JsonMapper jsonMapper;
    private QuestionServiceImpl service;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
        service = new QuestionServiceImpl(
            mapper,
            jsonMapper,
            imageUrlService,
            reviewSubmissionSupport,
            collectionMapper
        );
    }

    @Test
    void listRejectsKnowledgeFromAnotherSyllabusAndZeroPageSize() {
        QuestionQueryBo query = new QuestionQueryBo();
        query.setSyllabusVersionId("20");
        query.setKnowledgePointId("40");
        when(mapper.selectKnowledgeMetadata(List.of(40L))).thenReturn(List.of(knowledge(40L, 21L)));

        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service.list(query), "QUESTION_QUERY_INVALID");
        }

        QuestionQueryBo badPage = new QuestionQueryBo();
        badPage.setPageSize(0);
        assertQuestionError(() -> service.list(badPage), "QUESTION_QUERY_INVALID");
        verify(mapper, never()).selectQuestions(any(), any(), any(), any(), any(), any(), anyInt(), anyLong());
    }

    @Test
    void detailHandlesNoKnowledgeNullAnswerAndReadonlyStatus() {
        QuestionRows.Revision revision = revision("published");
        revision.setAnswer(null);
        when(mapper.selectRevision(QUESTION_ID, REVISION_ID, USER_ID)).thenReturn(revision);
        when(mapper.selectKnowledge(REVISION_ID)).thenReturn(List.of());
        when(mapper.selectExamSubjectOptions(9L)).thenReturn(List.of());
        when(mapper.selectImages(REVISION_ID)).thenReturn(List.of());
        when(mapper.selectOptions(REVISION_ID)).thenReturn(List.of());

        try (MockedStatic<LoginHelper> ignored = login()) {
            var detail = service.detail(Long.toString(QUESTION_ID), Long.toString(REVISION_ID));
            assertThat(detail.syllabusVersionId()).isNull();
            assertThat(detail.syllabusVersionName()).isNull();
            assertThat(detail.answer()).isNull();
            assertThat(detail.editableMode()).isEqualTo("readonly");
        }
        verify(mapper, never()).selectSyllabusLabel(anyLong());
    }

    @Test
    void saveReplaysAConcurrentWinnerWhenInsertLosesTheRace() throws Exception {
        String requestId = UUID.randomUUID().toString();
        var command = TestQuestionCommands.choice();
        String payloadHash = DigestUtil.sha256Hex(jsonMapper.writeValueAsString(command));
        QuestionRows.Idempotency completed = idempotency(
            payloadHash,
            "succeeded",
            QUESTION_ID,
            response(Map.of(
                "questionId", "100",
                "revisionId", "200",
                "revisionNo", 3,
                "rowVersion", "5",
                "status", "draft",
                "createdNewRevision", false,
                "updatedTime", "2026-08-12T10:00:00+08:00"
            ))
        );
        when(mapper.selectIdempotency("save_question_draft", requestId)).thenReturn(null, completed);
        when(mapper.insertIdempotency(anyLong(), eq("save_question_draft"), eq(requestId), eq(payloadHash),
            eq("question"), eq(QUESTION_ID), any())).thenReturn(0);

        var result = service.save("100", requestId, command);

        assertThat(result.rowVersion()).isEqualTo("5");
        verify(mapper, never()).lockRevision(anyLong(), anyLong(), any());
    }

    @Test
    void saveReportsCurrentRevisionWhenOptimisticUpdateLosesTheRace() {
        var command = TestQuestionCommands.choice();
        QuestionRows.Revision source = revision("draft");
        QuestionRows.Revision current = revision("draft");
        current.setRevisionId(201L);
        current.setRowVersion(6L);
        when(mapper.selectIdempotency(eq("save_question_draft"), anyString())).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("save_question_draft"), anyString(), anyString(),
            eq("question"), eq(QUESTION_ID), any())).thenReturn(1);
        when(mapper.lockRevision(QUESTION_ID, REVISION_ID, USER_ID)).thenReturn(source);
        when(mapper.selectKnowledge(REVISION_ID)).thenReturn(List.of());
        when(mapper.selectKnowledgeMetadata(argThat(ids -> ids.size() == 1 && ids.contains(40L))))
            .thenReturn(List.of(knowledge(40L, 20L)));
        when(mapper.updateRevision(anyLong(), anyLong(), anyString(), anyString(), any(), any(), anyString(),
            anyString(), any(), any(), anyString(), anyString(), any())).thenReturn(0);
        when(mapper.selectRevision(QUESTION_ID, REVISION_ID, USER_ID)).thenReturn(current);

        try (MockedStatic<LoginHelper> ignored = login()) {
            assertThatThrownBy(() -> service.save("100", UUID.randomUUID().toString(), command))
                .isInstanceOfSatisfying(QuestionException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("QUESTION_VERSION_CONFLICT");
                    assertThat(exception.getData().currentRevisionId()).isEqualTo("201");
                    assertThat(exception.getData().currentRowVersion()).isEqualTo("6");
                });
        }
    }

    @Test
    void deleteReturnsAConcurrentSuccessfulReplayWithoutReadingTheQuestion() {
        String requestId = UUID.randomUUID().toString();
        String payloadHash = DigestUtil.sha256Hex("delete:100");
        QuestionRows.Idempotency completed = idempotency(payloadHash, "succeeded", QUESTION_ID, "{}");
        when(mapper.selectIdempotency("delete_question", requestId)).thenReturn(null, completed);
        when(mapper.insertIdempotency(anyLong(), eq("delete_question"), eq(requestId), eq(payloadHash),
            eq("question"), eq(QUESTION_ID), any())).thenReturn(0);

        service.delete("100", requestId);

        verify(mapper, never()).selectRevision(anyLong(), any(), any());
        verify(mapper, never()).softDeleteQuestion(anyLong(), any());
    }

    @Test
    void submitReviewReturnsConcurrentReplayAndRejectsMissingRevision() throws Exception {
        String requestId = UUID.randomUUID().toString();
        QuestionRows.Revision draft = revision("draft");
        QuestionRows.Idempotency completed = idempotency(
            DigestUtil.sha256Hex("submit_review\n200\n"),
            "succeeded",
            REVISION_ID,
            response(Map.of(
                "questionId", "100",
                "revisionId", "200",
                "status", "pending_review",
                "rowVersion", "5"
            ))
        );
        when(mapper.lockReviewRevision(REVISION_ID, USER_ID)).thenReturn(draft);
        when(mapper.selectIdempotency("submit_review", requestId)).thenReturn(null, completed);
        when(reviewSubmissionSupport.validate(draft)).thenReturn(List.of());
        when(mapper.insertIdempotency(anyLong(), eq("submit_review"), eq(requestId), anyString(),
            eq("question_revision"), eq(REVISION_ID), any())).thenReturn(0);

        try (MockedStatic<LoginHelper> ignored = login()) {
            assertThat(service.submitReview("200", requestId).status()).isEqualTo("pending_review");
        }
        verify(reviewSubmissionSupport, never()).submitDraft(any(), any(), anyString(), anyString());

        String missingRequest = UUID.randomUUID().toString();
        when(mapper.lockReviewRevision(201L, USER_ID)).thenReturn(null);
        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service.submitReview("201", missingRequest), "QUESTION_REVISION_NOT_FOUND");
        }
    }

    @Test
    void approveReturnsConcurrentReplayAfterInsertConflict() throws Exception {
        String requestId = UUID.randomUUID().toString();
        String payloadHash = DigestUtil.sha256Hex("approve_question_revision\n200\n");
        QuestionRows.Idempotency completed = idempotency(
            payloadHash,
            "succeeded",
            QUESTION_ID,
            response(Map.of("questionId", "100", "revisionId", "200", "status", "published", "rowVersion", "5"))
        );
        when(mapper.selectReviewIdempotency(requestId)).thenReturn(null, null, completed);
        when(mapper.lockReviewRevision(REVISION_ID, USER_ID)).thenReturn(revision("pending_review"));
        when(mapper.insertReviewIdempotency(anyLong(), eq("approve_question_revision"), eq(requestId),
            eq(payloadHash), eq(QUESTION_ID), any())).thenReturn(0);

        try (MockedStatic<LoginHelper> ignored = login()) {
            assertThat(service.approve("200", requestId).status()).isEqualTo("published");
        }
        verify(mapper, never()).markReviewPublished(anyLong(), any());
    }

    @Test
    void rejectCoversLockedReplayMissingRowAndLostUpdate() throws Exception {
        String replayId = UUID.randomUUID().toString();
        String opinion = "依据不足";
        String payloadHash = DigestUtil.sha256Hex("reject_question_revision\n200\n" + opinion);
        QuestionRows.Idempotency completed = idempotency(
            payloadHash,
            "succeeded",
            QUESTION_ID,
            response(Map.of("questionId", "100", "revisionId", "200", "status", "rejected", "rowVersion", "5"))
        );
        when(mapper.selectReviewIdempotency(replayId)).thenReturn(null, completed);
        when(mapper.lockReviewRevision(REVISION_ID, USER_ID)).thenReturn(revision("pending_review"));
        try (MockedStatic<LoginHelper> ignored = login()) {
            assertThat(service.reject("200", replayId, opinion).status()).isEqualTo("rejected");
        }
        verify(mapper, never()).insertReviewIdempotency(anyLong(), anyString(), anyString(), anyString(), anyLong(), any());

        String missingId = UUID.randomUUID().toString();
        when(mapper.selectReviewIdempotency(missingId)).thenReturn(null);
        when(mapper.lockReviewRevision(201L, USER_ID)).thenReturn(null);
        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service.reject("201", missingId, opinion), "QUESTION_REVISION_NOT_FOUND");
        }

        String conflictId = UUID.randomUUID().toString();
        when(mapper.selectReviewIdempotency(conflictId)).thenReturn(null);
        when(mapper.lockReviewRevision(REVISION_ID, USER_ID)).thenReturn(revision("pending_review"));
        when(mapper.insertReviewIdempotency(anyLong(), eq("reject_question_revision"), eq(conflictId),
            anyString(), eq(QUESTION_ID), any())).thenReturn(1);
        when(mapper.markReviewRejected(REVISION_ID, USER_ID, opinion)).thenReturn(0);
        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service.reject("200", conflictId, opinion), "QUESTION_REVISION_CONFLICT");
        }
    }

    @Test
    void takeOfflineCoversMissingLockedReplayConcurrentReplayAndLostUpdate() throws Exception {
        String missingId = UUID.randomUUID().toString();
        when(mapper.selectReviewIdempotency(missingId)).thenReturn(null);
        when(mapper.lockReviewRevision(REVISION_ID, USER_ID)).thenReturn(null);
        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service.takeOffline("200", missingId), "QUESTION_REVISION_NOT_FOUND");
        }

        String lockedReplayId = UUID.randomUUID().toString();
        String payloadHash = DigestUtil.sha256Hex("offline_question_revision\n200\n");
        QuestionRows.Idempotency completed = idempotency(
            payloadHash,
            "succeeded",
            QUESTION_ID,
            response(Map.of("questionId", "100", "revisionId", "200", "status", "draft", "rowVersion", "5"))
        );
        when(mapper.selectReviewIdempotency(lockedReplayId)).thenReturn(null, completed);
        when(mapper.lockReviewRevision(REVISION_ID, USER_ID)).thenReturn(revision("published"));
        try (MockedStatic<LoginHelper> ignored = login()) {
            assertThat(service.takeOffline("200", lockedReplayId).status()).isEqualTo("draft");
        }

        String concurrentId = UUID.randomUUID().toString();
        when(mapper.selectReviewIdempotency(concurrentId)).thenReturn(null, null, completed);
        when(mapper.lockReviewRevision(REVISION_ID, USER_ID)).thenReturn(revision("published"));
        when(mapper.insertReviewIdempotency(anyLong(), eq("offline_question_revision"), eq(concurrentId),
            eq(payloadHash), eq(QUESTION_ID), any())).thenReturn(0);
        try (MockedStatic<LoginHelper> ignored = login()) {
            assertThat(service.takeOffline("200", concurrentId).status()).isEqualTo("draft");
        }

        String conflictId = UUID.randomUUID().toString();
        when(mapper.selectReviewIdempotency(conflictId)).thenReturn(null);
        when(mapper.lockReviewRevision(REVISION_ID, USER_ID)).thenReturn(revision("published"));
        when(mapper.insertReviewIdempotency(anyLong(), eq("offline_question_revision"), eq(conflictId),
            anyString(), eq(QUESTION_ID), any())).thenReturn(1);
        when(mapper.markPublishedDraft(REVISION_ID, USER_ID)).thenReturn(0);
        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service.takeOffline("200", conflictId), "QUESTION_REVISION_CONFLICT");
        }
    }

    @Test
    void approveAndRejectRejectNonPendingRevisionThroughTheirPublicActions() {
        String approveId = UUID.randomUUID().toString();
        when(mapper.selectReviewIdempotency(approveId)).thenReturn(null);
        when(mapper.lockReviewRevision(REVISION_ID, USER_ID)).thenReturn(revision("draft"));
        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service.approve("200", approveId), "QUESTION_REVIEW_INVALID_STATUS");
        }

        String rejectId = UUID.randomUUID().toString();
        when(mapper.selectReviewIdempotency(rejectId)).thenReturn(null);
        when(mapper.lockReviewRevision(REVISION_ID, USER_ID)).thenReturn(revision("published"));
        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service.reject("200", rejectId, "依据不足"), "QUESTION_REVIEW_INVALID_STATUS");
        }
    }

    private static QuestionRows.Revision revision(String status) {
        QuestionRows.Revision revision = new QuestionRows.Revision();
        revision.setQuestionId(QUESTION_ID);
        revision.setRevisionId(REVISION_ID);
        revision.setRevisionNo(3);
        revision.setRowVersion(4L);
        revision.setQuestionCode("Q-100");
        revision.setCertificationId(9L);
        revision.setCertificationName("架构师");
        revision.setExamSubjectId(30L);
        revision.setExamSubjectName("综合知识");
        revision.setQuestionType("CHOICE");
        revision.setStem("题干");
        revision.setAnswer("{\"schema_version\":\"1.0\",\"answer_type\":\"option_keys\",\"value\":[]}");
        revision.setStatus(status);
        return revision;
    }

    private static QuestionRows.Knowledge knowledge(long id, long syllabusId) {
        QuestionRows.Knowledge row = new QuestionRows.Knowledge();
        row.setKnowledgePointId(id);
        row.setSyllabusVersionId(syllabusId);
        row.setExamSubjectId(30L);
        row.setLeaf(true);
        row.setRelationRole("primary");
        row.setSortOrder(0);
        return row;
    }

    private static QuestionRows.Idempotency idempotency(
        String payloadHash,
        String status,
        long resourceId,
        String responseBody
    ) {
        QuestionRows.Idempotency record = new QuestionRows.Idempotency();
        record.setPayloadHash(payloadHash);
        record.setStatus(status);
        record.setResourceId(resourceId);
        record.setResponseBody(responseBody);
        return record;
    }

    private String response(Map<String, Object> data) throws Exception {
        return jsonMapper.writeValueAsString(Map.of("response", Map.of("data", data)));
    }

    private static MockedStatic<LoginHelper> login() {
        MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::isSuperAdmin).thenReturn(false);
        login.when(LoginHelper::getUserId).thenReturn(USER_ID);
        return login;
    }

    private static void assertQuestionError(ThrowingCall call, String code) {
        assertThatThrownBy(call::run)
            .isInstanceOfSatisfying(QuestionException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(code)
            );
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }

    private static final class TestQuestionCommands {
        private TestQuestionCommands() {
        }

        private static org.dromara.certmuse.question.domain.bo.QuestionSaveBo choice() {
            var command = new org.dromara.certmuse.question.domain.bo.QuestionSaveBo();
            command.setBaseRevisionId("200");
            command.setRowVersion("4");
            command.setExamSubjectId("30");
            command.setQuestionType("CHOICE");
            command.setStem("题干");

            var answer = new org.dromara.certmuse.question.domain.bo.QuestionSaveBo.AnswerBo();
            answer.setSchemaVersion("1.0");
            answer.setAnswerType("option_keys");
            answer.setSelectionMode("single");
            answer.setValue(List.of("A"));
            command.setAnswer(answer);

            var first = new org.dromara.certmuse.question.domain.bo.QuestionSaveBo.OptionBo();
            first.setLabel("A");
            first.setContent("正确");
            first.setSortOrder(1);
            var second = new org.dromara.certmuse.question.domain.bo.QuestionSaveBo.OptionBo();
            second.setLabel("B");
            second.setContent("错误");
            second.setSortOrder(2);
            command.setOptions(new java.util.ArrayList<>(List.of(first, second)));

            var binding = new org.dromara.certmuse.question.domain.bo.QuestionSaveBo.KnowledgeBindingBo();
            binding.setKnowledgePointId("40");
            binding.setRelationRole("primary");
            binding.setSortOrder(0);
            command.setKnowledgeBindings(new java.util.ArrayList<>(List.of(binding)));
            return command;
        }
    }
}
