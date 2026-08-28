package org.dromara.certmuse.catalog.service.impl;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.KnowledgeImportDiffInsert;
import org.dromara.certmuse.catalog.domain.KnowledgeImportDiffRow;
import org.dromara.certmuse.catalog.domain.KnowledgeImportMatch;
import org.dromara.certmuse.catalog.domain.KnowledgeImportPointRow;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.support.ImportJsonSchema;
import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;
import org.dromara.certmuse.catalog.support.KnowledgeImportMatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Generates and hashes the manual reconciliation set for a knowledge import. */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeImportReconciliationService {
    private final ImportMapper repository;
    private final KnowledgeImportMatcher matcher;
    private final JsonMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public void reconcile(long batchId) {
        long startedAt = System.currentTimeMillis();
        log.info("Knowledge import matching started, batchId={}", batchId);
        CmImportBatch batch = Objects.requireNonNull(repository.selectByIdForUpdate(batchId));
        if (!"knowledge_point".equals(batch.importType()) || !"validating".equals(batch.status())) {
            throw new IllegalStateException("Knowledge import batch is not ready for reconciliation");
        }
        long syllabusId = Objects.requireNonNull(batch.syllabusVersionId());
        List<KnowledgeImportPointRow> current = repository.selectCurrentKnowledgePoints(syllabusId);
        List<KnowledgeImportPointRow> incoming = repository.selectImportedKnowledgePoints(batchId);
        List<KnowledgeImportMatch> matches = matcher.match(incoming, current);
        Set<Long> matchedOld = new HashSet<>();
        List<KnowledgeImportDiffInsert> rows = new ArrayList<>();
        for (KnowledgeImportMatch match : matches) {
            KnowledgeImportPointRow next = match.incoming();
            KnowledgeImportPointRow old = match.existing();
            if (old == null) {
                rows.add(diff(batch, next, null, "add", "pending", match));
                continue;
            }
            matchedOld.add(old.getKnowledgePointId());
            boolean unchanged = unchanged(next, old);
            String action = unchanged ? "unchanged" : moved(next, old) ? "move" : "update";
            rows.add(diff(batch, next, old, action, unchanged ? "not_required" : "pending", match));
        }
        for (KnowledgeImportPointRow old : current) {
            if (!matchedOld.contains(old.getKnowledgePointId())) {
                rows.add(diff(batch, null, old, "delete", "pending", null));
            }
        }
        repository.deleteKnowledgeImportDiffs(batchId);
        if (!rows.isEmpty() && repository.insertKnowledgeImportDiffs(rows) != rows.size()) {
            throw new IllegalStateException("Knowledge import difference insert was incomplete");
        }
        String baseline = baselineHash(current);
        String resolution = resolutionHash(rows.stream().map(this::resolutionLine).toList());
        if (repository.updateKnowledgeImportHashes(batchId, baseline, resolution) != 1) {
            throw new IllegalStateException("Knowledge import hashes were not persisted");
        }
        log.info(
            "Knowledge import matching completed, batchId={}, incomingCount={}, baselineCount={}, diffCount={}, durationMs={}",
            batchId, incoming.size(), current.size(), rows.size(), System.currentTimeMillis() - startedAt
        );
    }

    /**
     * Records an empty tree as the validation baseline for a first import.
     * A newly created syllabus has nothing to reconcile, so its points can be
     * persisted directly after the ordinary precheck succeeds.
     */
    @Transactional(rollbackFor = Exception.class)
    public void initializeEmptyBaseline(long batchId) {
        CmImportBatch batch = Objects.requireNonNull(repository.selectByIdForUpdate(batchId));
        if (!"knowledge_point".equals(batch.importType()) || !"validating".equals(batch.status())) {
            throw new IllegalStateException("Knowledge import batch is not ready for baseline initialization");
        }
        List<KnowledgeImportPointRow> current = repository.selectCurrentKnowledgePoints(
            Objects.requireNonNull(batch.syllabusVersionId())
        );
        if (!current.isEmpty()) {
            throw new IllegalStateException("Knowledge tree is no longer empty and must be reconciled");
        }
        repository.deleteKnowledgeImportDiffs(batchId);
        String emptyBaseline = baselineHash(current);
        if (repository.updateKnowledgeImportHashes(batchId, emptyBaseline, resolutionHash(List.of())) != 1) {
            throw new IllegalStateException("Knowledge import baseline was not persisted");
        }
    }

    public String currentResolutionHash(long batchId) {
        return resolutionHash(repository.selectAllKnowledgeImportDiffs(batchId).stream().map(this::resolutionLine).toList());
    }

    public String baselineHash(List<KnowledgeImportPointRow> points) {
        List<String> lines = points.stream()
            .sorted(Comparator.comparing(KnowledgeImportPointRow::getKnowledgePointId))
            .map(point -> String.join("\u001f",
                String.valueOf(point.getKnowledgePointId()), String.valueOf(point.getRowVersion()),
                String.valueOf(point.getExamSubjectId()), String.valueOf(point.getParentKnowledgePointId()),
                value(point.getSyllabusNumber()), value(point.getSyllabusTitle()), String.valueOf(point.getTreeDepth()),
                String.valueOf(point.getSortOrder()), value(point.getDescription()), value(point.getStatus()), "0"
            )).toList();
        return resolutionHash(lines);
    }

    private KnowledgeImportDiffInsert diff(
        CmImportBatch batch,
        KnowledgeImportPointRow incoming,
        KnowledgeImportPointRow old,
        String action,
        String resolutionStatus,
        KnowledgeImportMatch match
    ) {
        Long oldId = old == null ? null : old.getKnowledgePointId();
        Long confirmedId = "not_required".equals(resolutionStatus) ? oldId : null;
        long subjectId = incoming == null ? old.getExamSubjectId() : incoming.getExamSubjectId();
        return new KnowledgeImportDiffInsert(
            IdUtil.getSnowflakeNextId(), batch.id(), incoming == null ? null : incoming.getImportRecordId(),
            oldId, oldId, confirmedId, subjectId, action, resolutionStatus,
            match == null ? null : score(match.totalScore()), evidence(match), changes(incoming, old), batch.createBy()
        );
    }

    private String evidence(KnowledgeImportMatch match) {
        Map<String, Object> value = VersionedJsonDocumentFactory.flatFields(
            ImportJsonSchema.KNOWLEDGE_IMPORT_MATCH_EVIDENCE, Map.of());
        value.put("title_score", match == null ? 0 : score(match.titleScore()));
        value.put("parent_score", match == null ? 0 : score(match.parentScore()));
        value.put("description_score", match == null ? 0 : score(match.descriptionScore()));
        value.put("children_score", match == null ? 0 : score(match.childrenScore()));
        value.put("number_score", match == null ? 0 : score(match.numberScore()));
        value.put("total_score", match == null ? 0 : score(match.totalScore()));
        value.put("candidate_gap", match == null ? 0 : score(match.candidateGap()));
        value.put("possible_move", match != null && match.possibleMove());
        value.put("ambiguous", match != null && match.ambiguous());
        value.put("split_merge_conflict", match != null && match.ambiguous());
        return json(value);
    }

    private String changes(KnowledgeImportPointRow incoming, KnowledgeImportPointRow old) {
        List<String> fields = new ArrayList<>();
        if (incoming == null || old == null) fields.add(incoming == null ? "deleted" : "added");
        else {
            changed(fields, "syllabusNumber", incoming.getSyllabusNumber(), old.getSyllabusNumber());
            changed(fields, "syllabusTitle", incoming.getSyllabusTitle(), old.getSyllabusTitle());
            changed(fields, "description", incoming.getDescription(), old.getDescription());
            changed(fields, "parent", incoming.getParentSyllabusNumber(), old.getParentSyllabusNumber());
            changed(fields, "treeDepth", incoming.getTreeDepth(), old.getTreeDepth());
            changed(fields, "sortOrder", incoming.getSortOrder(), old.getSortOrder());
            changed(fields, "status", incoming.getStatus(), old.getStatus());
        }
        return VersionedJsonDocumentFactory.json(objectMapper,
            ImportJsonSchema.KNOWLEDGE_IMPORT_CHANGED_FIELDS, Map.of("fields", fields));
    }

    private static boolean unchanged(KnowledgeImportPointRow incoming, KnowledgeImportPointRow old) {
        return Objects.equals(incoming.getSyllabusNumber(), old.getSyllabusNumber())
            && Objects.equals(incoming.getSyllabusTitle(), old.getSyllabusTitle())
            && Objects.equals(incoming.getDescription(), old.getDescription())
            && Objects.equals(incoming.getParentSyllabusNumber(), old.getParentSyllabusNumber())
            && incoming.getTreeDepth() == old.getTreeDepth()
            && incoming.getSortOrder() == old.getSortOrder()
            && Objects.equals(incoming.getStatus(), old.getStatus());
    }

    private static boolean moved(KnowledgeImportPointRow incoming, KnowledgeImportPointRow old) {
        return !Objects.equals(incoming.getParentSyllabusNumber(), old.getParentSyllabusNumber())
            || incoming.getTreeDepth() != old.getTreeDepth();
    }

    private String resolutionLine(KnowledgeImportDiffInsert row) {
        return String.join("\u001f", String.valueOf(row.id()), value(row.action()), value(row.resolutionStatus()),
            String.valueOf(row.importRecordId()), String.valueOf(row.confirmedKnowledgePointId()));
    }

    private String resolutionLine(KnowledgeImportDiffRow row) {
        return String.join("\u001f", String.valueOf(row.getId()), value(row.getAction()), value(row.getResolutionStatus()),
            String.valueOf(row.getImportRecordId()), String.valueOf(row.getConfirmedKnowledgePointId()));
    }

    private static String resolutionHash(List<String> lines) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String line : lines.stream().sorted().toList()) {
                digest.update(line.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash knowledge import state", exception);
        }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Unable to encode knowledge import metadata", exception); }
    }

    private static BigDecimal score(int basisPoints) {
        return BigDecimal.valueOf(basisPoints).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static void changed(List<String> fields, String field, Object next, Object old) {
        if (!Objects.equals(next, old)) fields.add(field);
    }

    private static String value(String value) { return value == null ? "" : value; }
}
