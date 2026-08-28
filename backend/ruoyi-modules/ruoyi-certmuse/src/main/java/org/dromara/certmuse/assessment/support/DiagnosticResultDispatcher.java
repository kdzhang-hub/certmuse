package org.dromara.certmuse.assessment.support;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.assessment.domain.DiagnosticItemRow;
import org.dromara.certmuse.assessment.domain.DiagnosticJobRow;
import org.dromara.certmuse.assessment.domain.DiagnosticLeafRow;
import org.dromara.certmuse.assessment.domain.DiagnosticReportRow;
import org.dromara.certmuse.assessment.domain.DiagnosticSessionRow;
import org.dromara.certmuse.assessment.mapper.DiagnosticMapper;
import org.dromara.certmuse.learning.service.MistakeFactRecorder;
import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Durable, stage-persisted worker for the frozen initial-diagnostic result pipeline. */
@Slf4j @Component @RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DiagnosticResultDispatcher {
    private static final List<String> STAGES = List.of("SUBMITTED", "SCORING", "REPORT_GENERATING", "PROFILE_GENERATING", "COMPLETED");
    private final DiagnosticMapper mapper;
    private final SubjectiveGradingTasks subjectiveTasks;
    private final JsonMapper jsonMapper;
    private final PlatformTransactionManager transactionManager;
    private final MistakeFactRecorder mistakeFactRecorder;

    /** Compatibility constructor retained for existing choice-only unit tests. */
    public DiagnosticResultDispatcher(DiagnosticMapper mapper, JsonMapper jsonMapper,
                                      PlatformTransactionManager transactionManager, MistakeFactRecorder mistakeFactRecorder) {
        this(mapper, null, jsonMapper, transactionManager, mistakeFactRecorder);
    }

    @Scheduled(fixedDelayString = "${certmuse.diagnostic.dispatch-delay-ms:1000}")
    public void dispatch() {
        DiagnosticJobRow job = mapper.claimResultJob();
        if (job == null) return;
        try {
            String next = nextStage(job);
            Boolean advanced = new TransactionTemplate(transactionManager).execute(status -> processStage(job, next));
            if (!Boolean.TRUE.equals(advanced)) {
                mapper.requeueJob(job.getId(), next);
            } else if ("COMPLETED".equals(next)) mapper.succeedJob(job.getId());
            else mapper.requeueJob(job.getId(), next);
        } catch (Exception exception) {
            String traceId = UUID.randomUUID().toString();
            log.error("Diagnostic result stage failed, jobId={}, stage={}, traceId={}",
                job.getId(), stage(job), traceId, exception);
            try {
                mapper.failJob(job.getId(), "traceId=" + traceId + ";message=诊断结果生成失败");
            } catch (RuntimeException persistenceException) {
                log.error("Unable to persist diagnostic result failure, jobId={}, traceId={}",
                    job.getId(), traceId, persistenceException);
            }
        }
    }

    /** Processes the next persisted pipeline stage for compatibility with focused worker tests. */
    public void process(DiagnosticJobRow job) {
        processStage(job, nextStage(job));
    }

    private boolean processStage(DiagnosticJobRow job, String stage) {
        long sessionId = sessionId(job);
        DiagnosticSessionRow session = mapper.lockSessionForWorker(sessionId);
        if (session == null) throw new IllegalStateException("diagnostic session missing");
        switch (stage) {
            case "SCORING" -> {
                if (!score(session)) return false;
            }
            case "REPORT_GENERATING" -> report(session);
            case "PROFILE_GENERATING" -> profile(session);
            case "COMPLETED" -> mapper.completeSession(sessionId);
            default -> throw new IllegalStateException("unsupported diagnostic stage " + stage);
        }
        mapper.advanceJobStage(job.getId(), stage);
        return true;
    }

    private boolean score(DiagnosticSessionRow session) {
        mapper.settleSession(session.getId());
        boolean waiting = false;
        for (DiagnosticItemRow item : mapper.selectSubmittedItems(session.getId())) {
            if (Set.of("CASE", "ESSAY").contains(item.getQuestionType())) {
                waiting |= !scoreSubjective(item);
                continue;
            }
            JsonNode answer = tree(item.getAnswerData());
            JsonNode key = tree(item.getGradingSnapshot()).path("answer");
            boolean correct = !answer.path("value").isNull() && answer.path("value").equals(key.path("value"));
            BigDecimal max = item.getReportScore();
            BigDecimal score = correct ? max : BigDecimal.ZERO;
            mapper.scoreAnswer(item.getAnswerId(), score, max, correct ? BigDecimal.ONE : BigDecimal.ZERO);
        }
        return !waiting;
    }

    private boolean scoreSubjective(DiagnosticItemRow item) {
        if ("graded".equals(item.getGradingStatus()) || "failed".equals(item.getGradingStatus())) return true;
        JsonNode answer = tree(item.getAnswerData());
        if (answer.path("value").isNull() || answer.path("value").asText().isBlank()) {
            mapper.scoreSubjectiveAnswer(item.getAnswerId(), BigDecimal.ZERO, item.getReportScore(), BigDecimal.ZERO,
                VersionedJsonDocumentFactory.json(jsonMapper, AssessmentJsonSchema.SUBJECTIVE_GRADING_RESULT,
                    Map.of("status", "BLANK")));
            return true;
        }
        SubjectiveGradingTasks.TaskState state = subjectiveTasks.ensure(item.getQuestionRevisionId(), item.getSessionQuestionId(),
            item.getAttemptId(), item.getPresentationSnapshot(), item.getGradingSnapshot(), item.getKnowledgeSnapshot(),
            answer.path("value").asText());
        if (Set.of("RUBRIC_PENDING", "PENDING", "PROCESSING").contains(state.status())) return false;
        mapper.updateAiRubricSnapshot(item.getSessionQuestionId(), state.rubric());
        if ("FAILED".equals(state.status())) {
            mapper.failSubjectiveAnswer(item.getAnswerId(), item.getReportScore(),
                VersionedJsonDocumentFactory.json(jsonMapper, AssessmentJsonSchema.SUBJECTIVE_GRADING_RESULT,
                    Map.of("errorCode", state.errorCode() == null ? "AI_GRADING_FAILED" : state.errorCode())));
            return true;
        }
        JsonNode result = tree(state.result());
        SubjectiveAnswerValidator.GradingResult validated = SubjectiveAnswerValidator.gradingResult(tree(state.rubric()), result);
        if (validated == null) {
            mapper.failSubjectiveAnswer(item.getAnswerId(), item.getReportScore(),
                VersionedJsonDocumentFactory.json(jsonMapper, AssessmentJsonSchema.SUBJECTIVE_GRADING_RESULT,
                    Map.of("errorCode", "AI_OUTPUT_INVALID")));
            return true;
        }
        BigDecimal score = item.getReportScore().multiply(validated.scoreRate()).setScale(2, RoundingMode.HALF_UP);
        mapper.scoreSubjectiveAnswer(item.getAnswerId(), score, item.getReportScore(), validated.scoreRate(), result.toString());
        return true;
    }

    private void report(DiagnosticSessionRow session) {
        List<DiagnosticItemRow> items = mapper.selectSubmittedItems(session.getId());
        int correct = 0, unanswered = 0;
        Map<Long, int[]> subjects = new TreeMap<>();
        for (DiagnosticItemRow item : items) {
            boolean blank = tree(item.getAnswerData()).path("value").isNull();
            boolean ok = correct(item);
            if (ok) correct++; if (blank) unanswered++;
            int[] counts = subjects.computeIfAbsent(item.getExamSubjectId(), ignored -> new int[2]);
            counts[0]++; if (ok) counts[1]++;
        }
        BigDecimal total = items.stream().map(item -> item.getScore() == null ? BigDecimal.ZERO : item.getScore())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal max = items.stream().map(DiagnosticItemRow::getReportScore).reduce(BigDecimal.ZERO, BigDecimal::add);
        String subjectScores = VersionedJsonDocumentFactory.json(jsonMapper, AssessmentJsonSchema.DIAGNOSTIC_REPORT, Map.of("totalQuestions", 50,
            "correctCount", correct, "unansweredCount", unanswered, "subjects", subjects.entrySet().stream()
                .map(e -> Map.of("examSubjectId", String.valueOf(e.getKey()), "questionCount", e.getValue()[0], "correctCount", e.getValue()[1])).toList()));
        String summary = VersionedJsonDocumentFactory.json(jsonMapper, AssessmentJsonSchema.DIAGNOSTIC_REPORT, Map.of("knowledgePoints", List.of(),
            "priorityDirections", List.of(), "confidence", "LOW", "dataStatus", "PROFILE_PENDING"));
        mapper.upsertReport(IdUtil.getSnowflakeNextId(), session.getId(), session.getUserId(), session.getGoalId(), session.getRuleVersionId(), total,
            max, subjectScores, summary);
    }

    private void profile(DiagnosticSessionRow session) {
        List<DiagnosticItemRow> items = mapper.selectSubmittedItems(session.getId());
        boolean partial = items.stream().anyMatch(item -> "failed".equals(item.getGradingStatus()));
        Map<Long, DiagnosticLeafRow> catalog = new TreeMap<>();
        for (DiagnosticLeafRow leaf : mapper.selectLeafCatalog(session.getId())) catalog.put(leaf.getId(), leaf);
        Map<Long, DiagnosticLeafRow> frozenMappedLeaves = new TreeMap<>();
        Map<Long, List<DiagnosticItemRow>> byLeaf = new TreeMap<>();
        for (DiagnosticItemRow item : items) {
            if ("failed".equals(item.getGradingStatus())) {
                continue;
            }
            Set<Long> mappedLeaves = new LinkedHashSet<>();
            for (JsonNode knowledge : tree(item.getKnowledgeSnapshot()).path("items")) {
                long leafId = knowledge.path("knowledgePointId").asLong();
                long subjectId = knowledge.path("examSubjectId").asLong();
                int importance = knowledge.path("importance").asInt();
                if (leafId <= 0 || subjectId <= 0 || importance < 1 || importance > 3) {
                    throw new IllegalStateException("diagnostic frozen leaf knowledge mapping is invalid");
                }
                DiagnosticLeafRow frozen = new DiagnosticLeafRow();
                frozen.setId(leafId);
                frozen.setExamSubjectId(subjectId);
                frozen.setImportance(importance);
                frozenMappedLeaves.putIfAbsent(leafId, frozen);
                mappedLeaves.add(leafId);
            }
            for (Long leafId : mappedLeaves) byLeaf.computeIfAbsent(leafId, ignored -> new ArrayList<>()).add(item);
        }
        if (byLeaf.isEmpty() && !partial) throw new IllegalStateException("diagnostic has no frozen leaf knowledge mapping");
        Map<Long, List<LeafResult>> subjectResults = new TreeMap<>();
        List<Map<String, Object>> knowledgeSummary = new ArrayList<>();
        long sequence = 1;
        for (Map.Entry<Long, List<DiagnosticItemRow>> entry : byLeaf.entrySet()) {
            DiagnosticLeafRow leaf = frozenMappedLeaves.get(entry.getKey());
            List<BigDecimal> evidence = new ArrayList<>();
            List<EvidenceFact> facts = new ArrayList<>();
            int correctCount = 0;
            for (DiagnosticItemRow item : entry.getValue()) {
                boolean correct = correct(item);
                if (correct) correctCount++;
                BigDecimal raw = item.getScoreRate() == null ? BigDecimal.ZERO : item.getScoreRate().multiply(BigDecimal.valueOf(100));
                BigDecimal coefficient = DiagnosticProfileRules.timeCoefficient(correct, item.getElapsedSeconds(), item.getTimerStatus(), item.getEstimatedSecondsSnapshot());
                evidence.add(DiagnosticProfileRules.adjustedEvidence(raw, coefficient));
                facts.add(new EvidenceFact(item, correct, raw, coefficient));
            }
            BigDecimal ability = DiagnosticProfileRules.initialAbility(evidence);
            String confidence = DiagnosticProfileRules.confidence(evidence.size());
            String status = DiagnosticProfileRules.profileStatus(ability, evidence.size(), false);
            long settlementId = mapper.insertSettlement(IdUtil.getSnowflakeNextId(), session.getUserId(), session.getGoalId(), leaf.getExamSubjectId(), leaf.getId(), session.getId(),
                ability, evidence.size(), session.getRuleVersionId(), "diagnostic-settlement-" + session.getId() + '-' + leaf.getId());
            for (EvidenceFact fact : facts) {
                DiagnosticItemRow item = fact.item();
                mapper.insertEvidence(IdUtil.getSnowflakeNextId(), settlementId, session.getUserId(), session.getGoalId(), session.getId(), item.getAttemptId(),
                    leaf.getExamSubjectId(), leaf.getId(), item.getEvidenceGroupKey(), "medium", fact.correct() ? BigDecimal.ONE : BigDecimal.ZERO, fact.raw(),
                    fact.correct() ? "positive" : "negative", item.getElapsedSeconds(), item.getTimerStatus(), fact.coefficient(), true,
                    session.getRuleVersionId(), sequence++);
                if (!fact.correct()) mistakeFactRecorder.record(session.getUserId(), session.getGoalId(), item.getAttemptId(),
                    leaf.getId(), item.getEvidenceGroupKey());
            }
            long profileId = mapper.upsertKnowledgeProfile(IdUtil.getSnowflakeNextId(), session.getUserId(), session.getGoalId(), leaf.getId(), ability, evidence.size(), confidence, status, session.getRuleVersionId());
            mapper.insertKnowledgeChange(IdUtil.getSnowflakeNextId(), profileId, settlementId, session.getRuleVersionId(),
                "diagnostic-change-" + session.getId() + '-' + leaf.getId(), VersionedJsonDocumentFactory.json(jsonMapper, AssessmentJsonSchema.PROFILE_CHANGE, Map.of("ability", ability, "status", status, "confidence", confidence)));
            LeafResult result = new LeafResult(leaf, ability, confidence, status, evidence.size(), correctCount);
            subjectResults.computeIfAbsent(leaf.getExamSubjectId(), ignored -> new ArrayList<>()).add(result);
            knowledgeSummary.add(result.report());
        }

        List<Map<String, Object>> subjectSummary = new ArrayList<>();
        List<LeafResult> allAssessed = subjectResults.values().stream().flatMap(Collection::stream).toList();
        for (Map.Entry<Long, List<LeafResult>> entry : subjectResults.entrySet()) {
            List<DiagnosticLeafRow> allLeaves = catalog.values().stream().filter(leaf -> Objects.equals(leaf.getExamSubjectId(), entry.getKey())).toList();
            SubjectMetrics metrics = subjectMetrics(entry.getValue(), allLeaves);
            long subjectProfileId = mapper.upsertSubjectProfile(IdUtil.getSnowflakeNextId(), session.getUserId(), session.getGoalId(), entry.getKey(), metrics.ability(),
                metrics.coverage(), metrics.mediumHighCoverage(), metrics.coreCovered(), metrics.confidence(), "provisional", 0, false, false, session.getRuleVersionId());
            subjectSummary.add(Map.of("examSubjectId", String.valueOf(entry.getKey()), "ability", metrics.ability(), "coverageRate", metrics.coverage(), "confidence", metrics.confidence()));
            mapper.insertSubjectChange(IdUtil.getSnowflakeNextId(), subjectProfileId, session.getRuleVersionId(),
                "diagnostic-subject-change-" + session.getId() + '-' + entry.getKey(), VersionedJsonDocumentFactory.json(jsonMapper, AssessmentJsonSchema.PROFILE_CHANGE, Map.of("ability", metrics.ability(), "coverageRate", metrics.coverage())));
        }
        SubjectMetrics overallMetrics = subjectMetrics(allAssessed, new ArrayList<>(catalog.values()));
        String overallSnapshot = VersionedJsonDocumentFactory.json(jsonMapper, AssessmentJsonSchema.PROFILE_OVERALL, Map.of("subjects", subjectSummary, "confidence", overallMetrics.confidence(), "dataStatus", !partial && overallMetrics.coverage().compareTo(BigDecimal.ONE) == 0 ? "INITIAL" : "PARTIAL"));
        long overallProfileId = mapper.upsertOverallProfile(IdUtil.getSnowflakeNextId(), session.getUserId(), session.getGoalId(), session.getRuleVersionId(), overallMetrics.ability(), overallSnapshot);
        mapper.insertOverallChange(IdUtil.getSnowflakeNextId(), overallProfileId, session.getRuleVersionId(),
            "diagnostic-overall-change-" + session.getId(), VersionedJsonDocumentFactory.json(jsonMapper, AssessmentJsonSchema.PROFILE_CHANGE, Map.of("ability", overallMetrics.ability(), "coverageRate", overallMetrics.coverage())));
        knowledgeSummary.sort(Comparator.comparing((Map<String, Object> value) -> riskRank((String) value.get("status")))
            .thenComparing(value -> (BigDecimal) value.get("ability")).thenComparing(value -> (String) value.get("knowledgePointId")));
        String profileSummary = VersionedJsonDocumentFactory.json(jsonMapper, AssessmentJsonSchema.DIAGNOSTIC_REPORT, Map.of("knowledgePoints", knowledgeSummary,
            "priorityDirections", knowledgeSummary.stream().limit(5).toList(), "confidence", overallMetrics.confidence(), "dataStatus", partial ? "PARTIAL" : "INITIAL_PROFILE"));
        BigDecimal max = items.stream().map(DiagnosticItemRow::getReportScore).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal score = items.stream().map(item -> item.getScore() == null ? BigDecimal.ZERO : item.getScore()).reduce(BigDecimal.ZERO, BigDecimal::add);
        mapper.upsertReport(IdUtil.getSnowflakeNextId(), session.getId(), session.getUserId(), session.getGoalId(), session.getRuleVersionId(), score,
            max, reportSubjects(session), profileSummary);
    }

    private String reportSubjects(DiagnosticSessionRow session) {
        DiagnosticReportRow report = mapper.selectReport(session.getUserId(), session.getId());
        return report == null ? VersionedJsonDocumentFactory.json(jsonMapper, AssessmentJsonSchema.DIAGNOSTIC_REPORT, Map.of()) : report.getSubjectScores();
    }
    private boolean correct(DiagnosticItemRow item) {
        if (item.getScoreRate() != null) return item.getScoreRate().compareTo(new BigDecimal("0.40")) >= 0;
        return !tree(item.getAnswerData()).path("value").isNull()
            && tree(item.getAnswerData()).path("value").equals(tree(item.getGradingSnapshot()).path("answer").path("value"));
    }
    private SubjectMetrics subjectMetrics(List<LeafResult> assessed, List<DiagnosticLeafRow> allLeaves) {
        BigDecimal weighted = BigDecimal.ZERO; int weight = 0, core = 0, assessedCore = 0, mediumHigh = 0;
        for (LeafResult result : assessed) { weighted = weighted.add(result.ability().multiply(BigDecimal.valueOf(result.leaf().getImportance()))); weight += result.leaf().getImportance(); if (result.leaf().getImportance() == 3) assessedCore++; if (!"low".equals(result.confidence())) mediumHigh++; }
        for (DiagnosticLeafRow leaf : allLeaves) if (leaf.getImportance() == 3) core++;
        BigDecimal ability = weight == 0 ? BigDecimal.ZERO : weighted.divide(BigDecimal.valueOf(weight), 8, RoundingMode.HALF_EVEN);
        BigDecimal coverage = allLeaves.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(assessed.size()).divide(BigDecimal.valueOf(allLeaves.size()), 8, RoundingMode.HALF_EVEN);
        BigDecimal mediumHighCoverage = allLeaves.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(mediumHigh).divide(BigDecimal.valueOf(allLeaves.size()), 8, RoundingMode.HALF_EVEN);
        String confidence = assessed.stream().allMatch(result -> "medium".equals(result.confidence())) ? "medium" : "low";
        return new SubjectMetrics(ability, coverage, mediumHighCoverage, core == assessedCore, confidence);
    }
    private record LeafResult(DiagnosticLeafRow leaf, BigDecimal ability, String confidence, String status, int questionCount, int correctCount) {
        Map<String, Object> report() { return Map.of("knowledgePointId", String.valueOf(leaf.getId()), "ability", ability, "status", status, "confidence", confidence, "correctCount", correctCount, "questionCount", questionCount); }
    }
    private record EvidenceFact(DiagnosticItemRow item, boolean correct, BigDecimal raw, BigDecimal coefficient) { }
    private record SubjectMetrics(BigDecimal ability, BigDecimal coverage, BigDecimal mediumHighCoverage, boolean coreCovered, String confidence) { }
    private int riskRank(String status) { return switch (status) { case "urgent" -> 0; case "weak" -> 1; default -> 2; }; }
    private BigDecimal average(Collection<BigDecimal> values) { return values.isEmpty() ? BigDecimal.ZERO : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_EVEN); }
    private long sessionId(DiagnosticJobRow job) { return Long.parseLong(tree(job.getPayload()).path("sessionId").asText()); }
    private String stage(DiagnosticJobRow job) { return tree(job.getPayload()).path("stage").asText("SUBMITTED"); }
    private String nextStage(DiagnosticJobRow job) { int index = STAGES.indexOf(stage(job)); return index < 0 || index + 1 >= STAGES.size() ? "COMPLETED" : STAGES.get(index + 1); }
    private JsonNode tree(String value) { try { return jsonMapper.readTree(value); } catch (Exception error) { throw new IllegalStateException("diagnostic snapshot is invalid", error); } }
    private String write(Object value) { try { return jsonMapper.writeValueAsString(value); } catch (Exception error) { throw new IllegalStateException("diagnostic result serialization failed", error); } }
}
