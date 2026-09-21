package org.dromara.certmuse.catalog.service.impl;

import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.question.service.PaperDraftCreationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class QuestionImportPersistenceServiceTest {
    @Mock private ImportMapper repository;
    @Mock private PaperDraftCreationService paperDraftCreationService;

    private QuestionImportPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new QuestionImportPersistenceService(repository, JsonMapper.builder().build(), paperDraftCreationService);
    }

    @Test
    void persistsNormalizedQuestionWithoutScoringPointWrites() {
        when(repository.selectByIdForUpdate(101L)).thenReturn(batch());
        when(repository.selectSuccessfulQuestionResult(101L)).thenReturn(List.of(normalizedQuestion()));
        when(repository.countQuestionSource("source-hash")).thenReturn(0);
        when(repository.completeQuestionImport(101L, 1)).thenReturn(1);

        service.persistBatch(101L);

        verify(repository).completeQuestionImport(101L, 1);
    }

    private static CmImportBatch batch() {
        return new CmImportBatch(
            101L, "request-101", null, 21L, 11L, "imports/question/101/source.zip", "zip-hash", 100L,
            "question", "question-zip/1.0", "{}", "importing", "persist_questions", BigDecimal.valueOf(95),
            1, 0, 0, 0, null, OffsetDateTime.parse("2026-08-17T10:00:00+08:00"), null, null, 7L, 8L, null, null, null
        );
    }

    private static String normalizedQuestion() {
        return "{\"source_hash\":\"source-hash\",\"exam_subject_id\":11,\"question_type\":\"ESSAY\","
            + "\"stem\":\"题干\",\"answer\":{\"schema_version\":\"1.0\",\"answer_type\":\"reference_text\",\"value\":\"答案\"},"
            + "\"analysis\":\"解析\",\"subject\":\"架构师\",\"qid\":\"q-1\",\"source\":\"rengong\","
            + "\"content_hash\":\"content-hash\",\"semantic_hash\":\"semantic-hash\",\"options\":[],\"images\":[],"
            + "\"knowledge_points\":[{\"id\":31,\"role\":\"primary\"}]}";
    }
}
