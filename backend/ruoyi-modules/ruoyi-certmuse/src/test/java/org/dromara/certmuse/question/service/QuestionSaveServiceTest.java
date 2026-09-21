package org.dromara.certmuse.question.service;

import org.dromara.certmuse.question.domain.QuestionRows;
import org.dromara.certmuse.question.domain.bo.QuestionSaveBo;
import org.dromara.certmuse.question.mapper.QuestionMapper;
import org.dromara.certmuse.question.mapper.CollectionMapper;
import org.dromara.certmuse.question.service.impl.QuestionReviewSubmissionSupport;
import org.dromara.certmuse.question.service.impl.QuestionServiceImpl;
import org.dromara.certmuse.question.support.QuestionException;
import org.dromara.certmuse.question.validation.QuestionSubmitReviewValidator;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class QuestionSaveServiceTest {

    @Mock
    private QuestionMapper mapper;
    @Mock
    private QuestionImageUrlService imageUrlService;
    @Mock
    private CollectionMapper collectionMapper;

    @Test
    void deletesRejectedQuestionWithoutPublishedHistoryOrBusinessReferences() {
        String requestId = UUID.randomUUID().toString();
        QuestionRows.Revision rejected = new QuestionRows.Revision();
        rejected.setQuestionId(100L);
        rejected.setRevisionId(200L);
        rejected.setStatus("rejected");

        when(mapper.selectIdempotency("delete_question", requestId)).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("delete_question"), eq(requestId), any(),
            eq("question"), eq(100L), any())).thenReturn(1);
        when(mapper.selectRevision(100L, null, 7L)).thenReturn(rejected);
        when(mapper.countPublishedHistory(100L)).thenReturn(0);
        when(mapper.countQuestionReferences(100L)).thenReturn(0);
        when(mapper.softDeleteQuestion(100L, 7L)).thenReturn(1);

        QuestionService service = service();
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);
            service.delete("100", requestId);
        }

        verify(mapper).softDeleteQuestion(100L, 7L);
    }

    @Test
    void rejectsSavingPublishedRevisionWithoutCreatingOrChangingAnyRevision() {
        String requestId = UUID.randomUUID().toString();
        QuestionRows.Revision published = new QuestionRows.Revision();
        published.setQuestionId(100L);
        published.setRevisionId(200L);
        published.setRevisionNo(2);
        published.setRowVersion(4L);
        published.setExamSubjectId(300L);
        published.setStatus("published");
        QuestionSaveBo command = new QuestionSaveBo();
        command.setBaseRevisionId("200");
        command.setRowVersion("4");
        command.setExamSubjectId("300");

        when(mapper.selectIdempotency("save_question_draft", requestId)).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("save_question_draft"), eq(requestId), any(),
            eq("question"), eq(100L), any())).thenReturn(1);
        when(mapper.lockRevision(100L, 200L, 7L)).thenReturn(published);

        QuestionService service = service();
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            assertThatThrownBy(() -> service.save("100", requestId, command))
                .isInstanceOfSatisfying(QuestionException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(409);
                    assertThat(exception.getErrorCode()).isEqualTo("QUESTION_STATUS_NOT_EDITABLE");
                });
        }

        verify(mapper, never()).updateRevision(anyLong(), anyLong(), anyString(), anyString(), anyString(), any(),
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any());
        verify(mapper, never()).insertRevision(anyLong(), anyLong(), anyInt(), anyString(), anyString(), any(),
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
        verify(mapper, never()).insertRevisionEvent(anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any());
        verify(mapper, never()).completeIdempotency(anyLong(), anyInt(), anyString());
    }

    private QuestionService service() {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        return new QuestionServiceImpl(mapper, jsonMapper, imageUrlService,
            new QuestionReviewSubmissionSupport(mapper, new QuestionSubmitReviewValidator(jsonMapper), jsonMapper),
            collectionMapper);
    }
}
