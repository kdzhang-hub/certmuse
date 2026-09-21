package org.dromara.certmuse.question.service;

import cn.hutool.crypto.digest.DigestUtil;
import org.dromara.certmuse.question.domain.CollectionRevisionRow;
import org.dromara.certmuse.question.domain.QuestionRows;
import org.dromara.certmuse.question.domain.bo.QuestionQueryBo;
import org.dromara.certmuse.question.domain.bo.QuestionSaveBo;
import org.dromara.certmuse.question.mapper.CollectionMapper;
import org.dromara.certmuse.question.mapper.QuestionMapper;
import org.dromara.certmuse.question.service.impl.QuestionReviewSubmissionSupport;
import org.dromara.certmuse.question.service.impl.QuestionServiceImpl;
import org.dromara.certmuse.question.support.QuestionException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("dev")
class QuestionServiceEdgeCaseTest {

    private static final long USER_ID = 7L;

    @Mock
    private QuestionMapper mapper;
    @Mock
    private QuestionImageUrlService imageUrlService;
    @Mock
    private QuestionReviewSubmissionSupport reviewSubmissionSupport;
    @Mock
    private CollectionMapper collectionMapper;

    @Test
    void rejectsInvalidListScopeFiltersBeforeQueryingQuestions() {
        assertListError(query -> query.setExamSubjectId("3"), "QUESTION_QUERY_INVALID");
        assertListError(query -> query.setKnowledgePointId("100"), "QUESTION_QUERY_INVALID");
        assertListError(query -> {
            query.setSyllabusVersionId("2");
            query.setExamSubjectId("3");
            when(mapper.countSubjectInSyllabus(2L, 3L)).thenReturn(0);
        }, "QUESTION_QUERY_INVALID");
        assertListError(query -> {
            query.setSyllabusVersionId("2");
            query.setKnowledgePointId("100");
            when(mapper.selectKnowledgeMetadata(List.of(100L))).thenReturn(List.of());
        }, "QUESTION_QUERY_INVALID");
        assertListError(query -> query.setQuestionType("UNKNOWN"), "QUESTION_QUERY_INVALID");
        assertListError(query -> query.setDifficulty("extreme"), "QUESTION_QUERY_INVALID");
        assertListError(query -> query.setStatus("offline"), "QUESTION_QUERY_INVALID");
        assertListError(query -> query.setKeyword("x".repeat(201)), "QUESTION_QUERY_INVALID");
        assertListError(query -> query.setPageNum(0), "QUESTION_QUERY_INVALID");
        assertListError(query -> query.setPageSize(101), "QUESTION_QUERY_INVALID");

        verify(mapper, never()).selectQuestions(any(), any(), any(), any(), any(), any(), anyInt(), anyLong());
    }

    @Test
    void listsScopedQuestionsForSuperAdminAndNormalizesFilters() {
        QuestionQueryBo query = new QuestionQueryBo();
        query.setKeyword("  architecture  ");
        query.setCertificationId("1");
        query.setSyllabusVersionId("2");
        query.setExamSubjectId("3");
        query.setKnowledgePointId("100");
        query.setQuestionType("CHOICE");
        query.setDifficulty("medium");
        query.setStatus("published");
        query.setPageNum(2);
        query.setPageSize(10);
        when(mapper.countSubjectInSyllabus(2L, 3L)).thenReturn(1);
        when(mapper.selectKnowledgeMetadata(List.of(100L))).thenReturn(List.of(knowledge(100L, 2L, 3L, true)));
        when(mapper.selectQuestions(any(), eq(1L), eq(2L), eq(3L), eq(100L), isNull(), eq(10), eq(10L)))
            .thenReturn(List.of());
        when(mapper.countQuestions(any(), eq(1L), eq(2L), eq(3L), eq(100L), isNull())).thenReturn(0L);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(true);
            assertThat(service().list(query).getTotal()).isZero();
        }

        assertThat(query.getKeyword()).isEqualTo("architecture");
    }

    @Test
    void reportsMissingQuestionAndRevisionWithDistinctCodes() {
        when(mapper.selectRevision(10L, null, USER_ID)).thenReturn(null);
        when(mapper.selectRevision(10L, 11L, USER_ID)).thenReturn(null);

        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service().detail("10", null), "QUESTION_NOT_FOUND");
            assertQuestionError(() -> service().detail("10", "11"), "QUESTION_REVISION_NOT_FOUND");
        }
    }

    @Test
    void refusesAmbiguousSyllabusAndCorruptStoredAnswer() {
        QuestionRows.Revision revision = revision("draft");
        when(mapper.selectRevision(10L, null, USER_ID)).thenReturn(revision);
        when(mapper.selectKnowledge(11L)).thenReturn(List.of(
            knowledge(100L, 2L, 3L, true), knowledge(101L, 4L, 3L, true)
        ));

        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service().detail("10", null), "QUESTION_SYLLABUS_IMMUTABLE");
        }

        when(mapper.selectKnowledge(11L)).thenReturn(List.of());
        revision.setAnswer("not-json");
        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service().detail("10", null), "QUESTION_OPERATION_FAILURE");
        }
    }

    @Test
    void validatesEveryChoiceAndCommonDraftBoundary() {
        assertSaveError(command -> command.getAnswer().setSchemaVersion("2.0"), "QUESTION_ANSWER_INVALID");
        assertSaveError(command -> command.setQuestionType("MATCH"), "QUESTION_TYPE_FIELDS_INVALID");
        assertSaveError(command -> command.setDifficulty("extreme"), "QUESTION_TYPE_FIELDS_INVALID");
        assertSaveError(command -> command.setEstimatedSeconds(0), "QUESTION_TYPE_FIELDS_INVALID");
        assertSaveError(command -> command.setStem("   "), "QUESTION_TYPE_FIELDS_INVALID");
        assertSaveError(command -> command.getKnowledgeBindings().clear(), "QUESTION_PRIMARY_KNOWLEDGE_INVALID");
        assertSaveError(command -> command.getKnowledgeBindings().add(binding("101", "primary", 1)),
            "QUESTION_PRIMARY_KNOWLEDGE_INVALID");
        assertSaveError(command -> command.getKnowledgeBindings().getFirst().setRelationRole("other"),
            "QUESTION_PRIMARY_KNOWLEDGE_INVALID");
        assertSaveError(command -> command.getKnowledgeBindings().add(binding("100", "secondary", 1)),
            "QUESTION_PRIMARY_KNOWLEDGE_INVALID");
        assertSaveError(command -> command.setOptions(new ArrayList<>()), "QUESTION_TYPE_FIELDS_INVALID");
        assertSaveError(command -> command.getOptions().add(option("A", "duplicate", 3)),
            "QUESTION_TYPE_FIELDS_INVALID");
        assertSaveError(command -> command.getOptions().getFirst().setSortOrder(2), "QUESTION_TYPE_FIELDS_INVALID");
        assertSaveError(command -> command.getAnswer().setAnswerType("reference_text"), "QUESTION_ANSWER_INVALID");
        assertSaveError(command -> command.getAnswer().setSelectionMode("multiple"), "QUESTION_ANSWER_INVALID");
        assertSaveError(command -> command.getAnswer().setValue(List.of("A", "B")), "QUESTION_ANSWER_INVALID");
        assertSaveError(command -> command.getAnswer().setValue(List.of("Z")), "QUESTION_ANSWER_INVALID");
        assertSaveError(command -> command.getAnswer().setValue(List.of(1)), "QUESTION_ANSWER_INVALID");
    }

    @Test
    void validatesKnowledgeMetadataAndSyllabusImmutability() {
        assertSaveErrorWithMetadata(List.of(), "QUESTION_KNOWLEDGE_NOT_FOUND");
        assertSaveErrorWithMetadata(List.of(knowledge(100L, 2L, 3L, false)), "QUESTION_KNOWLEDGE_NOT_LEAF");
        assertSaveErrorWithMetadata(List.of(knowledge(100L, 4L, 3L, true)), "QUESTION_SYLLABUS_IMMUTABLE");
        assertSaveErrorWithMetadata(List.of(knowledge(100L, 2L, 9L, true)), "QUESTION_PRIMARY_KNOWLEDGE_INVALID");

        QuestionRows.Knowledge anotherSyllabus = knowledge(101L, 4L, 3L, true);
        QuestionSaveBo command = choiceCommand();
        prepareSave(revision("draft"));
        when(mapper.selectKnowledge(11L))
            .thenReturn(List.of(knowledge(100L, 2L, 3L, true), anotherSyllabus));
        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service().save("10", UUID.randomUUID().toString(), command),
                "QUESTION_SYLLABUS_IMMUTABLE");
        }
    }

    @Test
    void validatesEverySubjectiveDraftBoundary() {
        assertSubjectiveError(command -> command.getOptions().add(option("A", "option", 1)),
            "QUESTION_TYPE_FIELDS_INVALID");
        assertSubjectiveError(command -> command.getAnswer().setAnswerType("option_keys"),
            "QUESTION_ANSWER_INVALID");
        assertSubjectiveError(command -> command.getAnswer().setValue(List.of("text")),
            "QUESTION_ANSWER_INVALID");
        assertSubjectiveError(command -> command.getAnswer().setValue(" "), "QUESTION_ANSWER_INVALID");
    }

    @Test
    void rejectsMissingRevisionSubjectMutationAndLostOptimisticUpdate() {
        assertSaveErrorWithSource(null, command -> { }, "QUESTION_REVISION_NOT_FOUND");
        QuestionSaveBo changedSubject = choiceCommand();
        changedSubject.setExamSubjectId("4");
        prepareSave(revision("draft"));
        when(mapper.selectKnowledgeMetadata(any())).thenReturn(List.of(knowledge(100L, 2L, 4L, true)));
        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service().save("10", UUID.randomUUID().toString(), changedSubject),
                "QUESTION_STATUS_NOT_EDITABLE");
        }

        QuestionRows.Revision source = revision("draft");
        prepareSave(source);
        when(mapper.updateRevision(anyLong(), anyLong(), anyString(), anyString(), any(), any(), anyString(),
            anyString(), any(), any(), anyString(), anyString(), any())).thenReturn(0);
        when(mapper.selectRevision(10L, 11L, USER_ID)).thenReturn(null);
        try (MockedStatic<LoginHelper> ignored = login()) {
            assertThatThrownBy(() -> service().save("10", UUID.randomUUID().toString(), choiceCommand()))
                .isInstanceOfSatisfying(QuestionException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo("QUESTION_VERSION_CONFLICT");
                    assertThat(error.getData().currentRevisionId()).isNull();
                });
        }
    }

    @Test
    void replaysCompletedSaveAndRejectsReusedOrRunningRequest() throws Exception {
        QuestionSaveBo command = choiceCommand();
        String requestId = UUID.randomUUID().toString();
        String payloadHash = DigestUtil.sha256Hex(json().writeValueAsString(command));
        QuestionRows.Idempotency completed = idempotency(payloadHash, "succeeded", 10L,
            json().writeValueAsString(Map.of("response", Map.of("data", Map.of(
                "questionId", "10", "revisionId", "11", "revisionNo", 1, "rowVersion", "1",
                "status", "draft", "createdNewRevision", false, "updatedTime", "2026-08-12T10:00:00+08:00"
            )))));
        when(mapper.selectIdempotency("save_question_draft", requestId)).thenReturn(completed);

        assertThat(service().save("10", requestId, command).rowVersion()).isEqualTo("1");
        verify(mapper, never()).lockRevision(anyLong(), anyLong(), any());

        String conflictingId = UUID.randomUUID().toString();
        when(mapper.selectIdempotency("save_question_draft", conflictingId))
            .thenReturn(idempotency("different", "succeeded", 10L, completed.getResponseBody()));
        assertQuestionError(() -> service().save("10", conflictingId, command), "QUESTION_QUERY_INVALID");

        String runningId = UUID.randomUUID().toString();
        when(mapper.selectIdempotency("save_question_draft", runningId))
            .thenReturn(idempotency(payloadHash, "processing", 10L, null));
        assertQuestionError(() -> service().save("10", runningId, command), "QUESTION_STATUS_NOT_EDITABLE");
    }

    @Test
    void enforcesDeleteStatusHistoryReferencesAndIdempotency() throws Exception {
        String completedId = UUID.randomUUID().toString();
        String deleteHash = DigestUtil.sha256Hex("delete:10");
        when(mapper.selectIdempotency("delete_question", completedId))
            .thenReturn(idempotency(deleteHash, "succeeded", 10L, "{}"));
        service().delete("10", completedId);
        verify(mapper, never()).selectRevision(eq(10L), isNull(), any());

        assertDeleteError(null, 0, 0, 0, "QUESTION_NOT_FOUND");
        assertDeleteError(revision("published"), 0, 0, 0, "QUESTION_DELETE_STATUS_INVALID");
        assertDeleteError(revision("draft"), 1, 0, 0, "QUESTION_DELETE_PUBLISHED_HISTORY");
        assertDeleteError(revision("draft"), 0, 1, 0, "QUESTION_DELETE_REFERENCED");
        assertDeleteError(revision("draft"), 0, 0, 0, "QUESTION_NOT_FOUND");

        String reusedId = UUID.randomUUID().toString();
        when(mapper.selectIdempotency("delete_question", reusedId))
            .thenReturn(idempotency("different", "succeeded", 10L, "{}"));
        assertQuestionError(() -> service().delete("10", reusedId), "QUESTION_QUERY_INVALID");
    }

    @Test
    void protectsReviewTransitionsAgainstMissingRowsConflictsAndBadReplayData() throws Exception {
        String approveId = UUID.randomUUID().toString();
        when(mapper.selectReviewIdempotency(approveId)).thenReturn(null);
        when(mapper.lockReviewRevision(11L, USER_ID)).thenReturn(null);
        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service().approve("11", approveId), "QUESTION_REVISION_NOT_FOUND");
        }

        QuestionRows.Revision pending = revision("pending_review");
        String updateConflictId = UUID.randomUUID().toString();
        when(mapper.selectReviewIdempotency(updateConflictId)).thenReturn(null);
        when(mapper.lockReviewRevision(11L, USER_ID)).thenReturn(pending);
        when(mapper.insertReviewIdempotency(anyLong(), eq("approve_question_revision"), eq(updateConflictId),
            anyString(), eq(10L), any())).thenReturn(1);
        when(mapper.markReviewPublished(11L, USER_ID)).thenReturn(0);
        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service().approve("11", updateConflictId), "QUESTION_REVISION_CONFLICT");
        }

        String badReplayId = UUID.randomUUID().toString();
        String hash = DigestUtil.sha256Hex("approve_question_revision\n11\n");
        when(mapper.selectReviewIdempotency(badReplayId))
            .thenReturn(idempotency(hash, "succeeded", 10L, "not-json"));
        assertQuestionError(() -> service().approve("11", badReplayId), "QUESTION_OPERATION_FAILURE");

        String runningReplayId = UUID.randomUUID().toString();
        when(mapper.selectReviewIdempotency(runningReplayId))
            .thenReturn(idempotency(hash, "processing", 10L, null));
        assertQuestionError(() -> service().approve("11", runningReplayId), "QUESTION_REVISION_CONFLICT");
    }

    @Test
    void abortsQuestionRejectionWhenAReferencingCollectionCannotTransition() {
        String requestId = UUID.randomUUID().toString();
        QuestionRows.Revision pending = revision("pending_review");
        CollectionRevisionRow collection = new CollectionRevisionRow();
        collection.setId(21L);
        collection.setCollectionId(20L);
        collection.setRowVersion(2L);
        collection.setStatus("pending_review");
        when(mapper.selectReviewIdempotency(requestId)).thenReturn(null);
        when(mapper.lockReviewRevision(11L, USER_ID)).thenReturn(pending);
        when(mapper.insertReviewIdempotency(anyLong(), eq("reject_question_revision"), eq(requestId),
            anyString(), eq(10L), any())).thenReturn(1);
        when(mapper.markReviewRejected(11L, USER_ID, "依据不足")).thenReturn(1);
        when(collectionMapper.lockPendingRevisionsByQuestionRevision(11L)).thenReturn(List.of(collection));
        when(collectionMapper.markRejected(eq(21L), eq(USER_ID), anyString())).thenReturn(0);

        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service().reject("11", requestId, "依据不足"),
                "QUESTION_REVISION_CONFLICT");
        }
    }

    private void assertListError(Consumer<QuestionQueryBo> arrange, String expectedCode) {
        QuestionQueryBo query = new QuestionQueryBo();
        arrange.accept(query);
        assertQuestionError(() -> service().list(query), expectedCode);
    }

    private void assertSaveError(Consumer<QuestionSaveBo> mutation, String expectedCode) {
        assertSaveErrorWithSource(revision("draft"), mutation, expectedCode);
    }

    private void assertSubjectiveError(Consumer<QuestionSaveBo> mutation, String expectedCode) {
        QuestionSaveBo command = subjectiveCommand();
        mutation.accept(command);
        assertSave(command, revision("draft"), expectedCode);
    }

    private void assertSaveErrorWithMetadata(List<QuestionRows.Knowledge> metadata, String expectedCode) {
        QuestionSaveBo command = choiceCommand();
        QuestionRows.Revision source = revision("draft");
        prepareSave(source);
        when(mapper.selectKnowledgeMetadata(any())).thenReturn(metadata);
        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service().save("10", UUID.randomUUID().toString(), command), expectedCode);
        }
    }

    private void assertSaveErrorWithSource(QuestionRows.Revision source, Consumer<QuestionSaveBo> mutation,
                                           String expectedCode) {
        QuestionSaveBo command = choiceCommand();
        mutation.accept(command);
        assertSave(command, source, expectedCode);
    }

    private void assertSave(QuestionSaveBo command, QuestionRows.Revision source, String expectedCode) {
        prepareSave(source);
        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service().save("10", UUID.randomUUID().toString(), command), expectedCode);
        }
    }

    private void prepareSave(QuestionRows.Revision source) {
        when(mapper.selectIdempotency(eq("save_question_draft"), anyString())).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("save_question_draft"), anyString(), anyString(),
            eq("question"), eq(10L), any())).thenReturn(1);
        when(mapper.lockRevision(10L, 11L, USER_ID)).thenReturn(source);
        when(mapper.selectKnowledge(11L)).thenReturn(List.of(knowledge(100L, 2L, 3L, true)));
        when(mapper.selectKnowledgeMetadata(any())).thenReturn(List.of(knowledge(100L, 2L, 3L, true)));
    }

    private void assertDeleteError(QuestionRows.Revision revision, int history, int references, int deleted,
                                   String expectedCode) {
        String requestId = UUID.randomUUID().toString();
        when(mapper.selectIdempotency("delete_question", requestId)).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("delete_question"), eq(requestId), anyString(),
            eq("question"), eq(10L), any())).thenReturn(1);
        when(mapper.selectRevision(10L, null, USER_ID)).thenReturn(revision);
        when(mapper.countPublishedHistory(10L)).thenReturn(history);
        when(mapper.countQuestionReferences(10L)).thenReturn(references);
        when(mapper.softDeleteQuestion(10L, USER_ID)).thenReturn(deleted);
        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service().delete("10", requestId), expectedCode);
        }
    }

    private static void assertQuestionError(ThrowingCall call, String expectedCode) {
        assertThatThrownBy(call::run)
            .isInstanceOfSatisfying(QuestionException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(expectedCode));
    }

    private QuestionServiceImpl service() {
        return new QuestionServiceImpl(mapper, json(), imageUrlService, reviewSubmissionSupport, collectionMapper);
    }

    private static JsonMapper json() {
        return JsonMapper.builder().findAndAddModules().build();
    }

    private static MockedStatic<LoginHelper> login() {
        MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::isSuperAdmin).thenReturn(false);
        login.when(LoginHelper::getUserId).thenReturn(USER_ID);
        return login;
    }

    private static QuestionRows.Revision revision(String status) {
        QuestionRows.Revision row = new QuestionRows.Revision();
        row.setQuestionId(10L);
        row.setRevisionId(11L);
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
        row.setStem("题干");
        row.setAnswer("{\"schema_version\":\"1.0\",\"answer_type\":\"option_keys\",\"selection_mode\":\"single\",\"value\":[\"A\"]}");
        row.setStatus(status);
        return row;
    }

    private static QuestionRows.Knowledge knowledge(long id, long syllabusId, long subjectId, boolean leaf) {
        QuestionRows.Knowledge row = new QuestionRows.Knowledge();
        row.setKnowledgePointId(id);
        row.setKnowledgePointLabel("知识点" + id);
        row.setRelationRole("primary");
        row.setSortOrder(0);
        row.setSyllabusVersionId(syllabusId);
        row.setExamSubjectId(subjectId);
        row.setLeaf(leaf);
        return row;
    }

    private static QuestionSaveBo choiceCommand() {
        QuestionSaveBo command = baseCommand("CHOICE");
        command.setOptions(new ArrayList<>(List.of(option("A", "选项A", 1), option("B", "选项B", 2))));
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
        command.setStem("题干");
        command.setKnowledgeBindings(new ArrayList<>(List.of(binding("100", "primary", 0))));
        return command;
    }

    private static QuestionSaveBo.OptionBo option(String label, String content, int order) {
        QuestionSaveBo.OptionBo option = new QuestionSaveBo.OptionBo();
        option.setLabel(label);
        option.setContent(content);
        option.setSortOrder(order);
        return option;
    }

    private static QuestionSaveBo.KnowledgeBindingBo binding(String id, String role, int order) {
        QuestionSaveBo.KnowledgeBindingBo binding = new QuestionSaveBo.KnowledgeBindingBo();
        binding.setKnowledgePointId(id);
        binding.setRelationRole(role);
        binding.setSortOrder(order);
        return binding;
    }

    private static QuestionRows.Idempotency idempotency(String hash, String status, long resourceId, String body) {
        QuestionRows.Idempotency row = new QuestionRows.Idempotency();
        row.setPayloadHash(hash);
        row.setStatus(status);
        row.setResourceId(resourceId);
        row.setResponseBody(body);
        return row;
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}
