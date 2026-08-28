package org.dromara.certmuse.catalog.service.impl;

import cn.hutool.core.util.IdUtil;
import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.PaperImportRow;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.support.QuestionImportWorker;
import org.dromara.certmuse.question.service.PaperDraftCreationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

/** Performs the all-or-nothing database phase of a question import. */
@Service
public class QuestionImportPersistenceService {
    private final ImportMapper repository;
    private final JsonMapper objectMapper;
    private final PaperDraftCreationService paperDraftCreationService;

    @Autowired
    public QuestionImportPersistenceService(
        ImportMapper repository,
        JsonMapper objectMapper,
        PaperDraftCreationService paperDraftCreationService
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.paperDraftCreationService = paperDraftCreationService;
    }

    /** Compatibility constructor retained for focused question-import unit tests. */
    public QuestionImportPersistenceService(ImportMapper repository, JsonMapper objectMapper) {
        this(repository, objectMapper, null);
    }

    @Transactional
    public void persistBatch(long batchId) {
        CmImportBatch batch = Optional.ofNullable(repository.selectByIdForUpdate(batchId))
            .orElseThrow(() -> new IllegalStateException("Import batch not found"));
        if (!"importing".equals(batch.status())) {
            return;
        }
        List<JsonNode> rows = repository.selectSuccessfulQuestionResult(batchId).stream().map(this::tree).toList();
        if (rows.isEmpty() || rows.size() != batch.validCount()) {
            throw new IllegalStateException("Validated question records changed");
        }
        Long userId = batch.createBy();
        if (userId == null) {
            throw new IllegalStateException("Question import batch has no creator");
        }
        int generated = 0;
        List<Long> revisionIds = new ArrayList<>(rows.size());
        for (JsonNode row : rows) {
            if ("REUSE".equals(row.path("resolution").asText())) {
                long revisionId = requiredLong(row, "reused_question_revision_id");
                revisionIds.add(revisionId);
                continue;
            }
            String sourceHash = row.path("source_hash").asText();
            if (repository.countQuestionSource(sourceHash) != 0) {
                throw new IllegalStateException("Question source already exists");
            }
            long subjectId = requiredLong(row, "exam_subject_id");
            revisionIds.add(insertQuestion(batch, subjectId, userId, row));
            generated++;
        }
        if ("paper".equals(batch.importType())) {
            persistPaper(batch, revisionIds, generated);
        } else if (repository.completeQuestionImport(batchId, generated) != 1) {
            throw new IllegalStateException("Question batch completion failed");
        }
    }

    private long insertQuestion(CmImportBatch batch, long subjectId, long userId, JsonNode row) {
        long questionId = IdUtil.getSnowflakeNextId();
        long revisionId = IdUtil.getSnowflakeNextId();
        String questionCode = "Q" + String.format("%019d", questionId);
        String answer = row.path("answer").isNull() ? null : row.path("answer").toString();
        repository.insertQuestion(questionId, questionCode, subjectId, row.path("source_hash").asText(), userId, batch.createDept());
            repository.insertQuestionRevision(
                revisionId, questionId, row.path("question_type").asText(), row.path("stem").asText(), answer,
                optionalText(row, "analysis"), optionalText(row, "subject"), row.path("qid").asText(),
                row.path("source").asText(), row.path("content_hash").asText(), row.path("semantic_hash").asText(), userId
            );
        repository.insertQuestionEvent(IdUtil.getSnowflakeNextId(), questionId, revisionId, batch.requestId(), userId);

        int optionOrder = 1;
        for (JsonNode option : row.path("options")) {
            repository.insertQuestionOption(
                IdUtil.getSnowflakeNextId(), revisionId, option.path("label").asText(),
                option.path("text").asText(), optionOrder++, userId
            );
        }
        int imageOrder = 1;
        for (JsonNode image : row.path("images")) {
            repository.insertQuestionImage(
                IdUtil.getSnowflakeNextId(), revisionId, imageOrder,
                QuestionImportWorker.imageKey(batch.sourceFileHash(), image.path("hash").asText(), image.path("extension").asText()),
                "图片" + imageOrder, userId
            );
            imageOrder++;
        }
        int knowledgeOrder = 0;
        for (JsonNode knowledge : row.path("knowledge_points")) {
            repository.insertQuestionKnowledge(
                IdUtil.getSnowflakeNextId(), revisionId, questionId, knowledge.path("id").asLong(), subjectId,
                knowledge.path("role").asText(knowledgeOrder == 0 ? "primary" : "secondary"), knowledgeOrder++
            );
        }
        return revisionId;
    }

    private void persistPaper(CmImportBatch batch, List<Long> revisionIds, int generated) {
        PaperImportRow paper = Optional.ofNullable(repository.selectPaperImportForUpdate(batch.id()))
            .orElseThrow(() -> new IllegalStateException("Paper import metadata not found"));
        if (paper.collectionId() != null || paper.collectionRevisionId() != null) {
            throw new IllegalStateException("Paper import was already persisted");
        }
        if (revisionIds.isEmpty() || revisionIds.size() != batch.validCount()) {
            throw new IllegalStateException("Paper revision count changed");
        }
        if (paperDraftCreationService == null) {
            throw new IllegalStateException("Paper draft creation service is unavailable");
        }
        PaperDraftCreationService.PaperDraftCreationResult draft = paperDraftCreationService.create(
            new PaperDraftCreationService.PaperDraftCreationCommand(
                paper.certificationId(), batch.syllabusVersionId(), paper.collectionName(), paper.collectionType(),
                paper.durationMinutes(), paper.examYear(), paper.examMonth(), paper.paperTypeCode(), paper.paperTypeName(),
                batch.createBy(), revisionIds
            )
        );
        long collectionId = draft.collectionId();
        long collectionRevisionId = draft.collectionRevisionId();
        if (repository.completePaperImport(batch.id(), generated, collectionId, collectionRevisionId) != 1
            || repository.updatePaperImportResult(batch.id(), collectionId, collectionRevisionId) != 1) {
            throw new IllegalStateException("Paper import completion failed");
        }
    }

    private static long requiredLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || value.asLong() <= 0) {
            throw new IllegalStateException("Validated question is missing " + field);
        }
        return value.asLong();
    }

    private static long requiredPositiveLong(JsonNode value, String field) {
        if (value == null || !value.isIntegralNumber() || value.asLong() <= 0) {
            throw new IllegalStateException("Validated question is missing " + field);
        }
        return value.asLong();
    }

    private JsonNode tree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid normalized question", exception);
        }
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
