package org.dromara.certmuse.question.service;

import cn.hutool.crypto.digest.DigestUtil;
import org.dromara.certmuse.question.domain.QuestionRows;
import org.dromara.certmuse.question.domain.bo.QuestionSaveBo;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Covers public question-authoring outcomes that are not represented by a normal successful draft.
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class QuestionServicePublicRemainingBehaviorTest {

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
        jsonMapper = JsonMapper.builder().findAndAddModules().build();
        service = new QuestionServiceImpl(mapper, jsonMapper, imageUrlService, reviewSubmissionSupport, collectionMapper);
    }

    @Test
    void saveReportsOperationFailureWhenACompletedIdempotencyResponseIsCorrupt() throws Exception {
        QuestionSaveBo command = choiceCommand(List.of());
        String requestId = UUID.randomUUID().toString();
        when(mapper.selectIdempotency("save_question_draft", requestId)).thenReturn(idempotency(
            DigestUtil.sha256Hex(jsonMapper.writeValueAsString(command)), "succeeded", QUESTION_ID, "not-json"
        ));

        assertQuestionError(() -> service.save("100", requestId, command), "QUESTION_OPERATION_FAILURE");
    }

    @Test
    void submitReviewReportsOperationFailureWhenACompletedIdempotencyResponseIsCorrupt() {
        String requestId = UUID.randomUUID().toString();
        when(mapper.lockReviewRevision(REVISION_ID, USER_ID)).thenReturn(revision("draft"));
        when(mapper.selectIdempotency("submit_review", requestId)).thenReturn(idempotency(
            DigestUtil.sha256Hex("submit_review\n200\n"), "succeeded", REVISION_ID, "not-json"
        ));

        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service.submitReview("200", requestId), "QUESTION_OPERATION_FAILURE");
        }
    }

    @Test
    void submitReviewLeavesTheDraftUnchangedWhenConcurrentSubmissionCannotBeReplayed() {
        String requestId = UUID.randomUUID().toString();
        QuestionRows.Revision draft = revision("draft");
        when(mapper.lockReviewRevision(REVISION_ID, USER_ID)).thenReturn(draft);
        when(mapper.selectIdempotency("submit_review", requestId)).thenAnswer(ignored -> null);
        when(reviewSubmissionSupport.validate(draft)).thenReturn(List.of());
        when(mapper.insertIdempotency(anyLong(), eq("submit_review"), eq(requestId), anyString(),
            eq("question_revision"), eq(REVISION_ID), any())).thenReturn(0);

        try (MockedStatic<LoginHelper> ignored = login()) {
            assertQuestionError(() -> service.submitReview("200", requestId), "QUESTION_REVISION_CONFLICT");
        }
    }

    @Test
    void saveAllowsAnUnboundChoiceDraftWithAnUnansweredSingleChoice() {
        QuestionSaveBo command = choiceCommand(List.of());
        prepareSuccessfulSave(revision("draft"));

        try (MockedStatic<LoginHelper> ignored = login()) {
            String requestId = UUID.randomUUID().toString();
            var result = service.save("100", requestId, command);
            assertThat(result.questionId()).isEqualTo("100");
            assertThat(result.revisionId()).isEqualTo("200");
            assertThat(result.rowVersion()).isEqualTo("5");
            assertThat(result.status()).isEqualTo("draft");
        }
    }

    @ParameterizedTest(name = "{0} draft may keep its scoring point independent of knowledge points")
    @ValueSource(strings = {"CASE", "ESSAY"})
    void saveAllowsAnUnboundSubjectiveDraftWithAnIndependentScoringPoint(String questionType) {
        QuestionSaveBo command = subjectiveCommand(questionType);
        prepareSuccessfulSave(revision("draft"));

        try (MockedStatic<LoginHelper> ignored = login()) {
            String requestId = UUID.randomUUID().toString();
            var result = service.save("100", requestId, command);
            assertThat(result.status()).isEqualTo("draft");
            assertThat(result.rowVersion()).isEqualTo("5");
        }
    }

    private void prepareSuccessfulSave(QuestionRows.Revision source) {
        when(mapper.selectIdempotency(eq("save_question_draft"), anyString())).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("save_question_draft"), anyString(), anyString(),
            eq("question"), eq(QUESTION_ID), any())).thenReturn(1);
        when(mapper.lockRevision(QUESTION_ID, REVISION_ID, USER_ID)).thenReturn(source);
        when(mapper.selectKnowledge(REVISION_ID)).thenReturn(List.of());
        when(mapper.updateRevision(anyLong(), anyLong(), anyString(), anyString(), any(), any(), anyString(),
            anyString(), any(), any(), anyString(), anyString(), any())).thenReturn(1);
    }

    private static QuestionSaveBo choiceCommand(List<String> answerKeys) {
        QuestionSaveBo command = baseCommand("CHOICE");
        command.setOptions(new ArrayList<>(List.of(option("A", "正确", 1), option("B", "错误", 2))));
        QuestionSaveBo.AnswerBo answer = new QuestionSaveBo.AnswerBo();
        answer.setSchemaVersion("1.0");
        answer.setAnswerType("option_keys");
        answer.setSelectionMode("single");
        answer.setValue(answerKeys);
        command.setAnswer(answer);
        return command;
    }

    private static QuestionSaveBo subjectiveCommand(String questionType) {
        QuestionSaveBo command = baseCommand(questionType);
        QuestionSaveBo.AnswerBo answer = new QuestionSaveBo.AnswerBo();
        answer.setSchemaVersion("1.0");
        answer.setAnswerType("reference_text");
        answer.setValue("参考答案");
        command.setAnswer(answer);
        return command;
    }

    private static QuestionSaveBo baseCommand(String questionType) {
        QuestionSaveBo command = new QuestionSaveBo();
        command.setBaseRevisionId("200");
        command.setRowVersion("4");
        command.setExamSubjectId("30");
        command.setQuestionType(questionType);
        command.setDifficulty("medium");
        command.setEstimatedSeconds(30);
        command.setStem("题干");
        command.setKnowledgeBindings(new ArrayList<>());
        command.setOptions(new ArrayList<>());
        return command;
    }

    private static QuestionSaveBo.OptionBo option(String label, String content, int sortOrder) {
        QuestionSaveBo.OptionBo option = new QuestionSaveBo.OptionBo();
        option.setLabel(label);
        option.setContent(content);
        option.setSortOrder(sortOrder);
        return option;
    }

    private static QuestionRows.Revision revision(String status) {
        QuestionRows.Revision revision = new QuestionRows.Revision();
        revision.setQuestionId(QUESTION_ID);
        revision.setRevisionId(REVISION_ID);
        revision.setRevisionNo(3);
        revision.setRowVersion(4L);
        revision.setExamSubjectId(30L);
        revision.setQuestionType("CHOICE");
        revision.setStatus(status);
        return revision;
    }

    private static QuestionRows.Idempotency idempotency(String payloadHash, String status, long resourceId,
                                                         String responseBody) {
        QuestionRows.Idempotency record = new QuestionRows.Idempotency();
        record.setPayloadHash(payloadHash);
        record.setStatus(status);
        record.setResourceId(resourceId);
        record.setResponseBody(responseBody);
        return record;
    }

    private static MockedStatic<LoginHelper> login() {
        MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::isSuperAdmin).thenReturn(false);
        login.when(LoginHelper::getUserId).thenReturn(USER_ID);
        return login;
    }

    private static void assertQuestionError(ThrowingCall call, String expectedCode) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(QuestionException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(expectedCode));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}
