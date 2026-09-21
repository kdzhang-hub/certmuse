package org.dromara.certmuse.question.service;

import org.dromara.certmuse.question.domain.QuestionKnowledgeMaintenanceRow;
import org.dromara.certmuse.question.mapper.QuestionMapper;
import org.dromara.certmuse.question.service.impl.QuestionKnowledgeMaintenanceServiceImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class QuestionKnowledgeMaintenanceServiceTest {
    private final QuestionMapper mapper = mock(QuestionMapper.class);
    private final QuestionKnowledgeMaintenanceService service = new QuestionKnowledgeMaintenanceServiceImpl(mapper);

    @Test
    void returnsWithoutQueryingWhenNothingWasDeletedOrNothingIsAffected() {
        assertThat(service.maintainAfterKnowledgeDeletion(null, 10L, 7L)).isZero();
        assertThat(service.maintainAfterKnowledgeDeletion(List.of(), 10L, 7L)).isZero();

        when(mapper.selectInvalidPublishedRevisions(List.of(100L))).thenReturn(List.of());
        assertThat(service.maintainAfterKnowledgeDeletion(List.of(100L), 10L, 7L)).isZero();
    }

    @Test
    void annotatesAffectedRevisionsAndWritesTheThreeMaintenanceArtifacts() {
        QuestionKnowledgeMaintenanceRow row = new QuestionKnowledgeMaintenanceRow();
        row.setQuestionId(1L);
        row.setRevisionId(2L);
        when(mapper.selectInvalidPublishedRevisions(List.of(100L))).thenReturn(List.of(row));
        when(mapper.batchReturnKnowledgeInvalidRevisionsToDraft(any(), eq(7L))).thenReturn(1);
        when(mapper.insertKnowledgeInvalidRevisionEvents(any())).thenReturn(1);
        when(mapper.insertKnowledgeInvalidAudits(any())).thenReturn(1);

        assertThat(service.maintainAfterKnowledgeDeletion(List.of(100L), 10L, 7L)).isEqualTo(1);
        assertThat(row.getEventId()).isPositive();
        assertThat(row.getAuditId()).isPositive();
        assertThat(row.getOperatorId()).isEqualTo(7L);
        assertThat(row.getRequestId()).isNotBlank();
        assertThat(row.getTraceId()).isNotBlank();
        verify(mapper).batchReturnKnowledgeInvalidRevisionsToDraft(List.of(row), 7L);
        verify(mapper).insertKnowledgeInvalidRevisionEvents(List.of(row));
        verify(mapper).insertKnowledgeInvalidAudits(List.of(row));
    }

    @Test
    void failsLoudlyWhenAnyMaintenanceStepIsIncomplete() {
        QuestionKnowledgeMaintenanceRow row = new QuestionKnowledgeMaintenanceRow();
        row.setRevisionId(2L);
        when(mapper.selectInvalidPublishedRevisions(List.of(100L))).thenReturn(List.of(row));
        when(mapper.batchReturnKnowledgeInvalidRevisionsToDraft(any(), eq(7L))).thenReturn(0);

        assertThatThrownBy(() -> service.maintainAfterKnowledgeDeletion(List.of(100L), 10L, 7L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Question knowledge maintenance update was incomplete");
    }
}
