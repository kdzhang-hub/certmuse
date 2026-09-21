package org.dromara.certmuse.question.service;

import org.dromara.certmuse.question.domain.CollectionItemRow;
import org.dromara.certmuse.question.mapper.CollectionMapper;
import org.dromara.certmuse.question.service.impl.PaperDraftCreationServiceImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class PaperDraftCreationServiceTest {
    private final CollectionMapper mapper = mock(CollectionMapper.class);
    private final PaperDraftCreationService service = new PaperDraftCreationServiceImpl(mapper);

    @Test
    void rejectsAnEmptyValidatedPaper() {
        var command = command(List.of());

        assertThatThrownBy(() -> service.create(command))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Paper must contain at least one question");
    }

    @Test
    void createsAWorkingCollectionAndScoresChoiceAndSubjectiveQuestions() {
        CollectionItemRow choice = metadata(101L, "CHOICE", "choice-schema");
        CollectionItemRow essay = metadata(102L, "ESSAY", "essay-schema");
        when(mapper.selectQuestionRevisionMetadata(List.of(101L, 102L), 7L)).thenReturn(List.of(choice, essay));

        var result = service.create(command(List.of(101L, 102L)));

        assertThat(result.collectionId()).isPositive();
        assertThat(result.collectionRevisionId()).isPositive();
        verify(mapper).insertCollection(anyLong(), eq(11L), eq(21L), any(), eq("模拟卷"), eq("SIMULATION"));
        verify(mapper).insertRevision(anyLong(), anyLong(), eq(1), eq("模拟卷"), eq("SIMULATION"),
            eq(11L), eq(21L), eq(120), eq(2), eq(BigDecimal.TEN.add(BigDecimal.ONE)));
        verify(mapper).insertItem(anyLong(), anyLong(), eq(101L), eq(1), eq(BigDecimal.ONE), eq("choice-schema"));
        verify(mapper).insertItem(anyLong(), anyLong(), eq(102L), eq(2), eq(BigDecimal.TEN), eq("essay-schema"));
    }

    @Test
    void rejectsMetadataThatIsMissingOrOutsideTheRequestedSyllabus() {
        when(mapper.selectQuestionRevisionMetadata(List.of(101L), null)).thenReturn(List.of());
        assertThatThrownBy(() -> service.create(command(List.of(101L))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Reused paper revision is no longer visible");

        CollectionItemRow wrongScope = metadata(101L, "CHOICE", "schema");
        wrongScope.setSyllabusVersionId(999L);
        when(mapper.selectQuestionRevisionMetadata(List.of(101L), 7L)).thenReturn(List.of(wrongScope));
        assertThatThrownBy(() -> service.create(command(List.of(101L))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Validated paper revision is unavailable or out of scope");
    }

    @Test
    void persistsPastPaperMetadataWithTheImportedDraft() {
        CollectionItemRow choice = metadata(101L, "CHOICE", "choice-schema");
        when(mapper.selectQuestionRevisionMetadata(List.of(101L), 7L)).thenReturn(List.of(choice));
        PaperDraftCreationService.PaperDraftCreationCommand pastPaper = new PaperDraftCreationService.PaperDraftCreationCommand(
            11L, 21L, "2025年系统架构设计师综合知识", "PAST_PAPER", 150,
            2025, 11, "AM", "综合知识", 7L, List.of(101L)
        );

        service.create(pastPaper);

        verify(mapper).insertCollection(anyLong(), eq(11L), eq(21L), any(),
            eq("2025年系统架构设计师综合知识"), eq("PAST_PAPER"), eq(2025), eq(11), eq("AM"), eq("综合知识"));
    }

    private static PaperDraftCreationService.PaperDraftCreationCommand command(List<Long> revisions) {
        return new PaperDraftCreationService.PaperDraftCreationCommand(
            11L, 21L, "模拟卷", "SIMULATION", 120, 7L, revisions);
    }

    private static CollectionItemRow metadata(long revisionId, String type, String schema) {
        CollectionItemRow row = new CollectionItemRow();
        row.setQuestionRevisionId(revisionId);
        row.setQuestionType(type);
        row.setAnswerSchema(schema);
        row.setSyllabusVersionId(21L);
        return row;
    }
}
