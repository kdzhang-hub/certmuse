package org.dromara.certmuse.question.service.impl;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.question.domain.CollectionItemRow;
import org.dromara.certmuse.question.mapper.CollectionMapper;
import org.dromara.certmuse.question.service.PaperDraftCreationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Creates the initial collection draft generated from a validated paper import. */
@Service
@RequiredArgsConstructor
public class PaperDraftCreationServiceImpl implements PaperDraftCreationService {

    private final CollectionMapper collectionMapper;

    @Override
    public PaperDraftCreationResult create(PaperDraftCreationCommand command) {
        if (command.questionRevisionIds().isEmpty()) {
            throw new IllegalStateException("Paper must contain at least one question");
        }
        List<CollectionItemRow> metadata = collectionMapper.selectQuestionRevisionMetadata(
            command.questionRevisionIds(), command.visibleUserId()
        );
        if (metadata.size() != command.questionRevisionIds().size()) {
            throw new IllegalStateException("Reused paper revision is no longer visible");
        }
        Map<Long, CollectionItemRow> byRevision = new HashMap<>();
        metadata.forEach(item -> byRevision.put(item.getQuestionRevisionId(), item));

        List<BigDecimal> scores = new ArrayList<>(command.questionRevisionIds().size());
        BigDecimal totalScore = BigDecimal.ZERO;
        for (Long revisionId : command.questionRevisionIds()) {
            CollectionItemRow item = byRevision.get(revisionId);
            if (item == null || !Objects.equals(item.getSyllabusVersionId(), command.syllabusVersionId())
                || item.getAnswerSchema() == null || item.getAnswerSchema().isBlank()) {
                throw new IllegalStateException("Validated paper revision is unavailable or out of scope");
            }
            BigDecimal score = "CHOICE".equals(item.getQuestionType()) ? BigDecimal.ONE : BigDecimal.TEN;
            scores.add(score);
            totalScore = totalScore.add(score);
        }

        long collectionId = IdUtil.getSnowflakeNextId();
        long collectionRevisionId = IdUtil.getSnowflakeNextId();
        String collectionCode = "C" + String.format("%019d", collectionId);
        if ("PAST_PAPER".equals(command.collectionType())) {
            validatePastPaperMetadata(command);
            collectionMapper.insertCollection(collectionId, command.certificationId(), command.syllabusVersionId(), collectionCode,
                command.collectionName(), command.collectionType(), command.examYear(), command.examMonth(),
                command.paperTypeCode(), command.paperTypeName());
        } else {
            collectionMapper.insertCollection(collectionId, command.certificationId(), command.syllabusVersionId(), collectionCode,
                command.collectionName(), command.collectionType());
        }
        collectionMapper.insertRevision(collectionRevisionId, collectionId, 1, command.collectionName(), command.collectionType(),
            command.certificationId(), command.syllabusVersionId(), command.durationMinutes(), command.questionRevisionIds().size(), totalScore);
        for (int index = 0; index < command.questionRevisionIds().size(); index++) {
            long revisionId = command.questionRevisionIds().get(index);
            collectionMapper.insertItem(IdUtil.getSnowflakeNextId(), collectionRevisionId, revisionId, index + 1,
                scores.get(index), byRevision.get(revisionId).getAnswerSchema());
        }
        return new PaperDraftCreationResult(collectionId, collectionRevisionId);
    }

    private static void validatePastPaperMetadata(PaperDraftCreationCommand command) {
        if (command.examYear() == null || command.examYear() < 1990 || command.examYear() > 2100
            || command.examMonth() == null || !List.of(5, 11).contains(command.examMonth())
            || command.paperTypeCode() == null || command.paperTypeCode().isBlank()
            || command.paperTypeName() == null || command.paperTypeName().isBlank()) {
            throw new IllegalStateException("Past-paper metadata is incomplete");
        }
    }
}
