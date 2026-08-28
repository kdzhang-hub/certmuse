package org.dromara.certmuse.learning.service.impl;

import cn.hutool.http.HtmlUtil;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.learning.domain.*;
import org.dromara.certmuse.learning.domain.bo.*;
import org.dromara.certmuse.learning.domain.vo.*;
import org.dromara.certmuse.learning.mapper.LearningHistoryMapper;
import org.dromara.certmuse.learning.service.LearningHistoryService;
import org.dromara.certmuse.learning.support.LearningHistoryException;
import org.dromara.certmuse.question.service.QuestionImageUrlService;
import org.dromara.certmuse.assessment.support.SubjectiveAnswerValidator;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Builds V1 learning-history projections exclusively from persisted facts and snapshots. */
@Service
@RequiredArgsConstructor
public class LearningHistoryServiceImpl implements LearningHistoryService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> QUESTION_TYPES = Set.of("CHOICE", "CASE", "ESSAY");
    private static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");
    private static final Set<String> CORRECTION_STATUSES = Set.of("NONE", "PENDING_CORRECTION", "CORRECTED");
    private static final Set<String> EXAM_TYPES = Set.of("INITIAL_DIAGNOSIS", "PAST_PAPER", "SIMULATION");
    private static final Set<String> PRACTICE_TYPES = Set.of("KNOWLEDGE_PRACTICE", "PAST_PAPER_PRACTICE");
    private final LearningHistoryMapper mapper;
    private final JsonMapper jsonMapper;
    private final QuestionImageUrlService imageUrlService;

    @Override
    public HistoryGoalsVo goals(long userId) {
        return new HistoryGoalsVo(mapper.selectGoals(userId).stream().map(x -> new HistoryGoalsVo.RowVo(
            id(x.getGoalId()), x.getCertificationName(), x.getSyllabusVersionName(), x.getTargetExamDate(),
            upper(x.getStatus()), x.isCurrent(), x.getLastRecordAt())).toList());
    }

    @Override
    public HistoryTaskPageVo tasks(long userId, HistoryTaskQueryBo query) {
        Page page = page(query.getPageNum(), query.getPageSize());
        String keyword = keyword(query.getKeyword());
        Long subjectId = optionalId(query.getSubjectId(), "subjectId");
        Long knowledgePointId = optionalId(query.getKnowledgePointId(), "knowledgePointId");
        Range range = range(query.getCompletedFrom(), query.getCompletedTo(), "completedFrom", "completedTo");
        long goalId = goal(userId, query.getGoalId());
        long total = mapper.countTasks(userId, goalId, keyword, subjectId, knowledgePointId, range.from(), range.to());
        List<HistoryTaskPageVo.RowVo> rows = mapper.selectTasks(userId, goalId, keyword, subjectId,
            knowledgePointId, range.from(), range.to(), page.offset(), page.size()).stream().map(this::taskRow).toList();
        return new HistoryTaskPageVo(rows, total);
    }

    @Override
    public HistoryTaskDetailVo task(long userId, String taskId, String goalIdValue) {
        long goalId = goal(userId, goalIdValue);
        long parsedTaskId = requiredId(taskId, "taskId");
        HistoryTaskRow task = mapper.selectTask(userId, goalId, parsedTaskId);
        if (task == null) throw failure(404, "HISTORY_TASK_NOT_FOUND", "学习任务不存在");
        checkTask(task);
        List<HistoryAttemptRow> attempts = mapper.selectTaskAttempts(userId, goalId, parsedTaskId);
        if (attempts.size() != task.getQuestionCount()) throw readFailure("完整任务缺少作答事实", null);
        TaskSnapshot snapshot = taskSnapshot(task);
        List<HistoryTaskDetailVo.QuestionVo> questions = attempts.stream()
            .map(x -> taskQuestion(x, snapshot)).toList();
        return new HistoryTaskDetailVo(id(task.getTaskId()), snapshot.title(), task.getCompletedAt(),
            new HistoryTaskDetailVo.LookupVo(snapshot.knowledgeId(), snapshot.knowledgeName()),
            new HistoryTaskDetailVo.SummaryVo(task.getQuestionCount(), task.getCorrectCount(),
                task.getIncorrectCount(), task.getSkippedCount()), questions);
    }

    @Override
    public HistoryQuestionPageVo questions(long userId, HistoryQuestionQueryBo query) {
        Page page = page(query.getPageNum(), query.getPageSize());
        String keyword = keyword(query.getKeyword());
        String type = enumeration(query.getQuestionType(), QUESTION_TYPES, "questionType");
        String difficulty = enumeration(query.getDifficulty(), DIFFICULTIES, "difficulty");
        String correction = enumeration(query.getCorrectionStatus(), CORRECTION_STATUSES, "correctionStatus");
        Long knowledgePointId = optionalId(query.getKnowledgePointId(), "knowledgePointId");
        Range range = range(query.getLastAnsweredFrom(), query.getLastAnsweredTo(), "lastAnsweredFrom", "lastAnsweredTo");
        long goalId = goal(userId, query.getGoalId());
        if (mapper.countInconsistentQuestions(userId, goalId, keyword, type, difficulty, knowledgePointId,
            range.from(), range.to()) > 0) {
            throw readFailure("错题订正状态不一致", null);
        }
        long total = mapper.countQuestions(userId, goalId, keyword, type, difficulty, knowledgePointId,
            correction, range.from(), range.to());
        List<HistoryQuestionPageVo.RowVo> rows = mapper.selectQuestions(userId, goalId, keyword, type, difficulty,
            knowledgePointId, correction, range.from(), range.to(), page.offset(), page.size()).stream()
            .map(this::questionRow).toList();
        return new HistoryQuestionPageVo(rows, total);
    }

    @Override
    public HistoryQuestionDetailVo question(long userId, String questionId, String goalIdValue) {
        long goalId = goal(userId, goalIdValue);
        long parsedQuestionId = requiredId(questionId, "questionId");
        List<HistoryAttemptRow> attempts = mapper.selectQuestionAttempts(userId, goalId, parsedQuestionId);
        if (attempts.isEmpty()) throw failure(404, "HISTORY_QUESTION_NOT_FOUND", "题目历史不存在");
        String status = correction(attempts.getFirst());
        List<HistoryQuestionDetailVo.AttemptVo> history = attempts.stream().map(this::attempt).toList();
        List<HistoryCorrectionEventRow> facts = mapper.selectCorrectionEvents(userId, goalId, parsedQuestionId);
        boolean correctedBefore = false;
        List<HistoryQuestionDetailVo.EventVo> events = new ArrayList<>();
        long wrong = 0, skipped = 0;
        List<HistoryCorrectionEventRow> chronological = facts.stream()
            .sorted(Comparator.comparing(HistoryCorrectionEventRow::getOccurredAt).thenComparingLong(HistoryCorrectionEventRow::getFactId))
            .toList();
        for (HistoryCorrectionEventRow fact : chronological) {
            String event;
            if (fact.isCorrection()) { event = "CORRECTED"; correctedBefore = true; }
            else if (fact.isSkipped()) { event = correctedBefore ? "REOPENED" : "SKIPPED"; skipped++; }
            else { event = correctedBefore ? "REOPENED" : "WRONG_ANSWER"; wrong++; }
            events.add(new HistoryQuestionDetailVo.EventVo(fact.getOccurredAt(), event, source(fact.getSessionType())));
        }
        Collections.reverse(events);
        Snapshot latest = snapshot(attempts.getFirst());
        return new HistoryQuestionDetailVo(id(parsedQuestionId), latest.preview(), attempts.size(), history,
            new HistoryQuestionDetailVo.CorrectionVo(status, wrong, skipped, events));
    }

    @Override
    public HistoryPracticePageVo practices(long userId, HistoryPracticeQueryBo query) {
        Page page = page(query.getPageNum(), query.getPageSize());
        String practiceType = enumeration(query.getPracticeType(), PRACTICE_TYPES, "practiceType");
        String sessionType = practiceType == null ? null : practiceSessionType(practiceType);
        Range range = range(query.getCompletedFrom(), query.getCompletedTo(), "completedFrom", "completedTo");
        long goalId = goal(userId, query.getGoalId());
        long total = mapper.countPractices(userId, goalId, sessionType, null, range.from(), range.to());
        List<HistoryPracticePageVo.RowVo> rows = mapper.selectPractices(userId, goalId, sessionType, null,
            range.from(), range.to(), page.offset(), page.size()).stream().map(this::practiceRow).toList();
        return new HistoryPracticePageVo(rows, total);
    }

    @Override
    public HistoryPracticeDetailVo practice(long userId, String sessionIdValue, String goalIdValue) {
        long goalId = goal(userId, goalIdValue);
        long sessionId = requiredId(sessionIdValue, "sessionId");
        HistoryPracticeSessionRow session = mapper.selectPractice(userId, goalId, sessionId, null, null, null);
        if (session == null) throw failure(404, "HISTORY_PRACTICE_NOT_FOUND", "练习历史不存在");
        PracticeCounts counts = practiceCounts(session);
        List<HistoryPracticeDetailVo.QuestionVo> questions = mapper.selectPracticeItems(userId, goalId, sessionId)
            .stream().map(this::practiceQuestion).toList();
        if (questions.size() != counts.questionCount()) throw readFailure("练习历史题目数量不一致", null);
        return new HistoryPracticeDetailVo(id(sessionId), practiceType(session.getSessionType()), session.getTitle(),
            new HistoryPracticeDetailVo.LookupVo(id(session.getSubjectId()), session.getSubjectName()),
            session.getStartedAt(), session.getCompletedAt(), new HistoryPracticeDetailVo.SummaryVo(
                counts.questionCount(), counts.answeredCount(), counts.correctCount(), counts.incorrectCount(),
                counts.unansweredCount()), questions);
    }

    @Override
    public HistoryExamPageVo exams(long userId, HistoryExamQueryBo query) {
        Page page = page(query.getPageNum(), query.getPageSize());
        String examType = enumeration(query.getExamType(), EXAM_TYPES, "examType");
        Range range = range(query.getCompletedFrom(), query.getCompletedTo(), "completedFrom", "completedTo");
        long goalId = goal(userId, query.getGoalId());
        if (mapper.countInconsistentExams(userId, goalId) > 0) {
            throw readFailure("考试历史完成事实不一致", null);
        }
        long total = mapper.countExams(userId, goalId, examType, range.from(), range.to());
        List<HistoryExamRowVo> rows = mapper.selectExams(userId, goalId, examType, range.from(), range.to(),
            page.offset(), page.size()).stream().map(this::examRow).toList();
        return new HistoryExamPageVo(rows, total);
    }

    @Override
    public HistoryExamDetailVo exam(long userId, String sessionIdValue, String goalIdValue) {
        long goalId = goal(userId, goalIdValue);
        long sessionId = requiredId(sessionIdValue, "sessionId");
        HistoryExamRow exam = mapper.selectExam(userId, goalId, sessionId);
        if (exam == null) throw failure(404, "HISTORY_EXAM_NOT_FOUND", "考试历史不存在");
        HistoryExamRowVo row = examRow(exam);
        List<HistoryExamDetailVo.QuestionVo> questions = mapper.selectExamQuestions(userId, goalId, sessionId)
            .stream().map(this::examQuestion).toList();
        return new HistoryExamDetailVo(row.sessionId(), row.examType(), row.title(), row.subject(), row.completedAt(),
            row.result(), row.durationSeconds(), row.durationLimitSeconds(), row.durationStatus(), questions);
    }

    private HistoryTaskPageVo.RowVo taskRow(HistoryTaskRow task) {
        checkTask(task);
        TaskSnapshot snapshot = taskSnapshot(task);
        return new HistoryTaskPageVo.RowVo(id(task.getTaskId()), snapshot.title(),
            new HistoryTaskPageVo.LookupVo(snapshot.knowledgeId(), snapshot.knowledgeName()),
            new HistoryTaskPageVo.LookupVo(id(task.getSubjectId()), task.getSubjectName()), task.getQuestionCount(),
            task.getCorrectCount(), task.getIncorrectCount(), task.getSkippedCount(), task.getCompletedAt());
    }

    private HistoryQuestionPageVo.RowVo questionRow(HistoryAttemptRow row) {
        Snapshot snapshot = snapshot(row);
        return new HistoryQuestionPageVo.RowVo(id(row.getQuestionId()), snapshot.preview(), snapshot.type(),
            upper(row.getDifficultySnapshot()), questionKnowledge(snapshot), row.getAttemptCount(), row.getAnsweredAt(),
            new HistoryQuestionPageVo.AttemptVo(source(row.getSessionType()), result(row), row.getScoreRate()), correction(row));
    }

    private HistoryTaskDetailVo.QuestionVo taskQuestion(HistoryAttemptRow row, TaskSnapshot task) {
        JsonNode presentation = checkedSnapshot(row.getPresentationSnapshot(), "冻结题面读取失败");
        JsonNode grading = checkedSnapshot(row.getGradingSnapshot(), "冻结答案读取失败");
        if (!"CHOICE".equals(presentation.path("questionType").asText())
            || presentation.path("stem").asText().isBlank() || !presentation.path("options").isArray()) {
            throw readFailure("每日任务题面事实不一致", null);
        }
        List<HistoryTaskDetailVo.OptionVo> options = presentation.path("options").valueStream().map(item -> {
            String label = item.path("label").asText();
            String content = item.path("content").asText();
            if (label.isBlank() || content.isBlank()) throw readFailure("冻结选项读取失败", null);
            return new HistoryTaskDetailVo.OptionVo(label, content);
        }).toList();
        List<String> selected = row.getAnswerData() == null ? List.of()
            : labels(checkedSnapshot(row.getAnswerData(), "历史答案读取失败").path("value"), "历史答案读取失败");
        List<String> correct = labels(grading.path("answer").path("value"), "冻结正确答案读取失败");
        boolean unanswered = row.isSkipped() || selected.isEmpty();
        Boolean correctResult = unanswered ? null : BigDecimal.ONE.compareTo(row.getScoreRate()) == 0;
        Snapshot snapshot = snapshot(row);
        return new HistoryTaskDetailVo.QuestionVo(id(row.getQuestionId()), row.getQuestionOrder(), "CHOICE",
            upper(row.getDifficultySnapshot()), presentation.path("stem").asText(),
            presentation.path("selectionMode").asText("single"), options,
            frozenImages(presentation).stream()
                .map(x -> new HistoryTaskDetailVo.ImageVo(x.sortOrder(), x.url(), x.altText())).toList(),
            taskKnowledge(snapshot, task),
            new HistoryTaskDetailVo.SubmissionVo(selected, unanswered, row.getAnsweredAt()),
            new HistoryTaskDetailVo.ResultVo(correctResult, unanswered ? null : decimal(row.getScore()),
                decimal(row.getMaxScore()), unanswered ? null : row.getScoreRate(), correct,
                grading.path("analysis").asText(null)));
    }

    private HistoryPracticePageVo.RowVo practiceRow(HistoryPracticeSessionRow row) {
        PracticeCounts counts = practiceCounts(row);
        List<HistoryPracticePageVo.LookupVo> knowledge = practiceKnowledge(row.getKnowledgeSnapshots()).stream()
            .map(x -> new HistoryPracticePageVo.LookupVo(x.id(), x.name())).toList();
        return new HistoryPracticePageVo.RowVo(id(row.getSessionId()), practiceType(row.getSessionType()), row.getTitle(),
            new HistoryPracticePageVo.LookupVo(id(row.getSubjectId()), row.getSubjectName()), knowledge,
            counts.questionCount(), counts.answeredCount(), counts.correctCount(), counts.incorrectCount(),
            counts.unansweredCount(), row.getCompletedAt());
    }

    private HistoryPracticeDetailVo.QuestionVo practiceQuestion(HistoryPracticeItemRow row) {
        JsonNode presentation = checkedSnapshot(row.getPresentationSnapshot(), "冻结题面读取失败");
        JsonNode grading = checkedSnapshot(row.getGradingSnapshot(), "冻结答案读取失败");
        String type = presentation.path("questionType").asText();
        if (!QUESTION_TYPES.contains(type) || presentation.path("stem").asText().isBlank()) {
            throw readFailure("练习题面事实不一致", null);
        }
        boolean choice = "CHOICE".equals(type);
        List<HistoryPracticeDetailVo.OptionVo> options = choice
            ? presentation.path("options").valueStream().map(x -> {
                String label = x.path("label").asText();
                String content = x.path("content").asText();
                if (label.isBlank() || content.isBlank()) throw readFailure("冻结选项读取失败", null);
                return new HistoryPracticeDetailVo.OptionVo(label, content);
            }).toList() : List.of();
        List<HistoryPracticeDetailVo.ImageVo> images = frozenImages(presentation).stream()
            .map(x -> new HistoryPracticeDetailVo.ImageVo(x.sortOrder(), x.url(), x.altText())).toList();
        List<HistoryPracticeDetailVo.LookupVo> knowledge = knowledgeFromSnapshot(row.getKnowledgeSnapshot()).stream()
            .map(x -> new HistoryPracticeDetailVo.LookupVo(x.id(), x.name())).toList();
        List<String> correct = choice
            ? labels(grading.path("answer").path("value"), "冻结正确答案读取失败") : List.of();
        String reference = choice ? null : grading.path("answer").path("value").asText(null);
        String analysis = grading.path("analysis").asText(null);
        HistoryPracticeDetailVo.SubmissionVo submission = null;
        List<String> selected = List.of();
        String textAnswer = null;
        boolean unanswered = true;
        String status = row.getGradingStatus();
        if (row.getAttemptId() != null) {
            JsonNode answer = checkedSnapshot(row.getAnswerData(), "历史答案读取失败");
            selected = choice ? labels(answer.path("value"), "历史答案读取失败") : List.of();
            textAnswer = choice ? null : answer.path("value").asText(null);
            unanswered = Boolean.TRUE.equals(row.getSkipped())
                || (choice ? selected.isEmpty() : textAnswer == null || textAnswer.isBlank());
            if (!unanswered && !Set.of("graded", "pending", "processing", "failed").contains(status)) {
                throw readFailure("练习作答评分状态不一致", null);
            }
            submission = new HistoryPracticeDetailVo.SubmissionVo(selected, textAnswer, unanswered, row.getSubmittedAt());
        }
        boolean graded = "graded".equals(status);
        if (graded && (row.getScore() == null || row.getMaxScore() == null || row.getScoreRate() == null)) {
            throw readFailure("练习评分事实不一致", null);
        }
        String gradingSource = row.getGradingSource();
        if (!choice && row.getAttemptId() != null && gradingSource == null) gradingSource = "AI";
        GradingProjection projection = graded && !choice && "AI".equals(gradingSource)
            ? gradingProjection(row.getAiRubricSnapshot(), row.getGradingResult()) : GradingProjection.empty();
        Boolean correctResult = choice && graded ? BigDecimal.ONE.compareTo(row.getScoreRate()) == 0 : null;
        return new HistoryPracticeDetailVo.QuestionVo(id(row.getQuestionId()), row.getQuestionOrder(), type,
            upper(row.getDifficultySnapshot()), presentation.path("stem").asText(),
            choice ? presentation.path("selectionMode").asText("single") : null, options, images, knowledge, submission,
            new HistoryPracticeDetailVo.ResultVo(correctResult, graded ? decimal(row.getScore()) : null,
                row.getMaxScore() == null ? null : decimal(row.getMaxScore()), graded ? row.getScoreRate() : null,
                correct, reference, analysis, status, gradingSource, row.getGradingRevisionNo(), projection.feedback(),
                projection.items().stream().map(x -> new HistoryPracticeDetailVo.GradingItemVo(
                    x.code(), x.description(), x.weight(), x.scoreRate())).toList()));
    }

    private HistoryQuestionDetailVo.AttemptVo attempt(HistoryAttemptRow row) {
        Snapshot snapshot = snapshot(row);
        return new HistoryQuestionDetailVo.AttemptVo(id(row.getAttemptId()), id(row.getSessionId()),
            source(row.getSessionType()), row.getAnsweredAt(), snapshot.preview(), snapshot.type(),
            upper(row.getDifficultySnapshot()), detailKnowledge(snapshot), result(row), answerSummary(row, snapshot),
            decimal(row.getScore()), decimal(row.getMaxScore()), row.getScoreRate(), row.isSkipped());
    }

    private HistoryExamRowVo examRow(HistoryExamRow row) {
        if (!EXAM_TYPES.contains(row.getExamType()) || row.getTitle() == null || row.getTitle().isBlank()
            || row.getCompletedAt() == null || !Set.of("AVAILABLE", "UNAVAILABLE").contains(row.getDurationStatus())) {
            throw readFailure("考试历史事实不一致", null);
        }
        HistoryExamRowVo.SubjectVo subject = row.getSubjectId() == null ? null
            : new HistoryExamRowVo.SubjectVo(id(row.getSubjectId()), row.getSubjectName());
        return new HistoryExamRowVo(id(row.getSessionId()), row.getExamType(), row.getTitle(), subject,
            row.getCompletedAt(), examResult(row), row.getDurationSeconds(), row.getDurationLimitSeconds(),
            row.getDurationStatus());
    }

    private HistoryExamDetailVo.QuestionVo examQuestion(HistoryExamQuestionRow row) {
        JsonNode presentation = checkedSnapshot(row.getPresentationSnapshot(), "冻结题面读取失败");
        JsonNode grading = checkedSnapshot(row.getGradingSnapshot(), "冻结答案读取失败");
        String type = presentation.path("questionType").asText();
        if (!QUESTION_TYPES.contains(type) || presentation.path("stem").asText().isBlank()) {
            throw readFailure("考试题面事实不一致", null);
        }
        boolean choice = "CHOICE".equals(type);
        JsonNode answer = row.getAnswerData() == null ? null : checkedSnapshot(row.getAnswerData(), "考试答案读取失败");
        List<String> selected = choice && answer != null ? labels(answer.path("value"), "考试答案读取失败") : List.of();
        String textAnswer = !choice && answer != null ? answer.path("value").asText(null) : null;
        boolean unanswered = row.getSubmittedAt() == null || Boolean.TRUE.equals(row.getSkipped())
            || answer == null || (choice ? selected.isEmpty() : textAnswer == null || textAnswer.isBlank());
        String status = row.getGradingStatus();
        if (!unanswered && !Set.of("graded", "pending", "processing", "failed").contains(status)) {
            throw readFailure("考试作答评分状态不一致", null);
        }
        boolean graded = "graded".equals(status);
        if (graded && (row.getScore() == null || row.getMaxScore() == null || row.getScoreRate() == null
            || row.getScoreRate().signum() < 0 || row.getScoreRate().compareTo(BigDecimal.ONE) > 0)) {
            throw readFailure("考试评分事实不一致", null);
        }
        List<String> correct = choice ? labels(grading.path("answer").path("value"), "冻结正确答案读取失败") : List.of();
        String reference = choice ? null : grading.path("answer").path("value").asText(null);
        String analysis = grading.path("analysis").asText(null);
        String feedback = null;
        List<HistoryExamDetailVo.GradingItemVo> gradingItems = List.of();
        if (graded && !choice && "AI".equals(row.getGradingSource())) {
            GradingProjection projection = gradingProjection(row.getAiRubricSnapshot(), row.getGradingResult());
            feedback = projection.feedback();
            gradingItems = projection.items().stream().map(x -> new HistoryExamDetailVo.GradingItemVo(
                x.code(), x.description(), x.weight(), x.scoreRate())).toList();
        }
        Boolean correctResult = choice && graded ? BigDecimal.ONE.compareTo(row.getScoreRate()) == 0 : null;
        HistoryExamDetailVo.SubmissionVo submission = new HistoryExamDetailVo.SubmissionVo(
            selected, textAnswer, unanswered, row.getSubmittedAt());
        HistoryExamDetailVo.ResultVo result = new HistoryExamDetailVo.ResultVo(correctResult,
            graded ? decimal(row.getScore()) : null, row.getMaxScore() == null ? null : decimal(row.getMaxScore()),
            graded ? row.getScoreRate() : null, correct, reference, analysis, status, row.getGradingSource(),
            row.getGradingRevisionNo(), feedback, gradingItems);
        return new HistoryExamDetailVo.QuestionVo(id(row.getQuestionId()), row.getQuestionOrder(), type,
            upper(row.getDifficultySnapshot()), presentation.path("stem").asText(), examOptions(presentation),
            frozenImages(presentation).stream()
                .map(x -> new HistoryExamDetailVo.ImageVo(x.sortOrder(), x.url(), x.altText())).toList(),
            submission, result);
    }

    private GradingProjection gradingProjection(String rubricRaw, String resultRaw) {
        if (resultRaw == null || resultRaw.isBlank()) {
            return GradingProjection.empty();
        }
        JsonNode result = tree(resultRaw);
        if (!result.isObject()) return GradingProjection.empty();
        String feedback = result.path("feedback").asText(null);
        if (feedback != null && feedback.isBlank()) feedback = null;
        if (rubricRaw == null || rubricRaw.isBlank()) return new GradingProjection(feedback, List.of());
        JsonNode rubric = tree(rubricRaw);
        if (!rubric.isObject()) return new GradingProjection(feedback, List.of());
        SubjectiveAnswerValidator.GradingResult validated = SubjectiveAnswerValidator.gradingResult(rubric, result);
        if (validated == null) return new GradingProjection(feedback, List.of());
        Map<String, JsonNode> rubricByCode = new LinkedHashMap<>();
        for (JsonNode item : rubric.path("items")) rubricByCode.put(item.path("code").asText(), item);
        List<GradingItem> items = new ArrayList<>();
        for (JsonNode itemResult : validated.itemResults()) {
            JsonNode item = rubricByCode.get(itemResult.path("code").asText());
            if (item == null) return GradingProjection.empty();
            items.add(new GradingItem(itemResult.path("code").asText(),
                item.path("description").asText(), decimalValue(item.path("weight"), "冻结评分权重读取失败"),
                decimalValue(itemResult.path("scoreRate"), "AI评分项读取失败")));
        }
        return new GradingProjection(validated.feedback(), List.copyOf(items));
    }

    private HistoryExamResultVo examResult(HistoryExamRow row) {
        String reportStatus = row.getReportStatus();
        if (!Set.of("AVAILABLE", "PROCESSING", "FAILED").contains(reportStatus)) {
            throw readFailure("考试报告状态不一致", null);
        }
        if ("AVAILABLE".equals(reportStatus) && (row.getScore() == null || row.getMaxScore() == null
            || row.getScoreRate() == null || row.getScore().signum() < 0 || row.getMaxScore().signum() <= 0
            || row.getScore().compareTo(row.getMaxScore()) > 0 || row.getScoreRate().signum() < 0
            || row.getScoreRate().compareTo(BigDecimal.ONE) > 0)) {
            throw readFailure("考试报告分数不一致", null);
        }
        if (!"AVAILABLE".equals(reportStatus) && (row.getScore() != null || row.getMaxScore() != null
            || row.getScoreRate() != null)) {
            throw readFailure("不可用考试报告包含分数", null);
        }
        return new HistoryExamResultVo(reportStatus, decimal(row.getScore()), decimal(row.getMaxScore()), row.getScoreRate());
    }

    private void checkTask(HistoryTaskRow task) {
        if (task.getCompletedAt() == null || task.getPendingCount() != 0 || task.getQuestionCount() <= 0
            || task.getQuestionCount() != task.getCorrectCount() + task.getIncorrectCount() + task.getSkippedCount()) {
            throw readFailure("学习任务历史数据不一致", null);
        }
    }

    private String correction(HistoryAttemptRow row) {
        return correction(row.getPendingErrorCount(), row.getSuccessfulCorrectionCount(), row.getErrorCount());
    }

    private String correction(int pendingErrorCount, int successfulCorrectionCount, int errorCount) {
        if (pendingErrorCount > 0) return "PENDING_CORRECTION";
        if (successfulCorrectionCount > 0) return "CORRECTED";
        if (errorCount == 0) return "NONE";
        throw readFailure("错题订正状态不一致", null);
    }

    private String result(HistoryAttemptRow row) {
        if (row.isSkipped()) return "SKIPPED";
        if ("pending".equals(row.getGradingStatus())) return "PENDING_SCORING";
        if (!"graded".equals(row.getGradingStatus()) || row.getScoreRate() == null) {
            throw readFailure("作答评分状态不一致", null);
        }
        return BigDecimal.ONE.compareTo(row.getScoreRate()) == 0 ? "CORRECT" : "INCORRECT";
    }

    private Snapshot snapshot(HistoryAttemptRow row) {
        return snapshot(row.getPresentationSnapshot(), row.getKnowledgeSnapshot());
    }

    private Snapshot snapshot(String presentationSnapshot, String knowledgeSnapshot) {
        try {
            JsonNode presentation = tree(presentationSnapshot);
            JsonNode knowledge = tree(knowledgeSnapshot);
            String type = presentation.path("questionType").asText();
            String stem = presentation.path("stem").asText();
            if (!QUESTION_TYPES.contains(type) || stem.isBlank() || !presentation.has("schema_version")
                || !knowledge.has("schema_version")) throw new IllegalArgumentException("invalid snapshot");
            return new Snapshot(type, preview(stem), presentation, knowledge);
        } catch (LearningHistoryException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw readFailure("冻结题目读取失败", exception);
        }
    }

    private TaskSnapshot taskSnapshot(HistoryTaskRow row) {
        try {
            JsonNode display = tree(row.getDisplaySnapshot());
            String title = display.path("title").asText();
            String knowledge = display.path("knowledgePoint").asText();
            if (!display.has("schema_version") || title.isBlank() || knowledge.isBlank()) throw new IllegalArgumentException();
            return new TaskSnapshot(title, id(row.getKnowledgePointId()), knowledge);
        } catch (RuntimeException exception) {
            if (exception instanceof LearningHistoryException history) throw history;
            throw readFailure("冻结任务读取失败", exception);
        }
    }

    private List<HistoryTaskDetailVo.LookupVo> taskKnowledge(Snapshot snapshot, TaskSnapshot task) {
        JsonNode items = snapshot.knowledge().path("items");
        if (!items.isArray()) throw readFailure("冻结知识点读取失败", null);
        List<HistoryTaskDetailVo.LookupVo> result = items.valueStream().map(x -> {
            String itemId = x.path("knowledgePointId").asText(x.path("id").asText());
            String name = x.path("knowledgePointName").asText(x.path("name").asText());
            if (name.isBlank() && itemId.equals(task.knowledgeId())) name = task.knowledgeName();
            return new HistoryTaskDetailVo.LookupVo(itemId, name);
        }).toList();
        if (result.stream().anyMatch(x -> x.id().isBlank() || x.name().isBlank()))
            throw readFailure("冻结知识点名称缺失", null);
        return result;
    }
    private List<HistoryQuestionPageVo.LookupVo> questionKnowledge(Snapshot snapshot) {
        return knowledge(snapshot).stream().map(x -> new HistoryQuestionPageVo.LookupVo(x.id(), x.name())).toList();
    }
    private List<HistoryQuestionDetailVo.LookupVo> detailKnowledge(Snapshot snapshot) {
        return knowledge(snapshot).stream().map(x -> new HistoryQuestionDetailVo.LookupVo(x.id(), x.name())).toList();
    }
    private List<Lookup> knowledge(Snapshot snapshot) {
        JsonNode items = snapshot.knowledge().path("items");
        if (!items.isArray()) throw readFailure("冻结知识点读取失败", null);
        List<Lookup> result = items.valueStream().map(x -> new Lookup(x.path("knowledgePointId").asText(x.path("id").asText()),
            x.path("knowledgePointName").asText(x.path("name").asText()))).toList();
        if (result.stream().anyMatch(x -> x.id().isBlank() || x.name().isBlank())) {
            throw readFailure("冻结知识点名称缺失", null);
        }
        return result;
    }

    private PracticeCounts practiceCounts(HistoryPracticeSessionRow row) {
        if (!Set.of("self_practice", "past_paper_practice").contains(row.getSessionType())
            || row.getTitle() == null || row.getTitle().isBlank() || row.getSubjectId() <= 0
            || row.getSubjectName() == null || row.getSubjectName().isBlank() || row.getCompletedAt() == null
            || row.getQuestionCount() <= 0 || row.getAnsweredCount() < 0 || row.getCorrectCount() < 0
            || row.getIncorrectCount() < 0 || row.getAnsweredCount() > row.getQuestionCount()
            || row.getAnsweredCount() < row.getCorrectCount() + row.getIncorrectCount()) {
            throw readFailure("练习历史统计事实不一致", null);
        }
        return new PracticeCounts(row.getQuestionCount(), row.getAnsweredCount(), row.getCorrectCount(),
            row.getIncorrectCount(), row.getQuestionCount() - row.getAnsweredCount());
    }

    private List<Lookup> practiceKnowledge(String raw) {
        JsonNode snapshots = tree(raw);
        if (!snapshots.isArray()) throw readFailure("练习知识点摘要读取失败", null);
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (JsonNode snapshot : snapshots) {
            for (Lookup item : knowledgeFromSnapshot(snapshot)) values.putIfAbsent(item.id(), item.name());
        }
        return values.entrySet().stream().map(x -> new Lookup(x.getKey(), x.getValue())).toList();
    }

    private List<Lookup> knowledgeFromSnapshot(String raw) {
        return knowledgeFromSnapshot(checkedSnapshot(raw, "冻结知识点读取失败"));
    }

    private List<Lookup> knowledgeFromSnapshot(JsonNode snapshot) {
        JsonNode items = snapshot.path("items");
        if (!items.isArray()) throw readFailure("冻结知识点读取失败", null);
        List<Lookup> values = items.valueStream().map(x -> new Lookup(
            x.path("knowledgePointId").asText(x.path("id").asText()),
            x.path("knowledgePointName").asText(x.path("name").asText()))).toList();
        if (values.stream().anyMatch(x -> x.id().isBlank() || x.name().isBlank())) {
            throw readFailure("冻结知识点读取失败", null);
        }
        return values;
    }

    private List<FrozenImage> frozenImages(JsonNode presentation) {
        try {
            JsonNode images = presentation.path("images");
            if (!images.isArray()) return List.of();
            List<FrozenImage> values = new ArrayList<>();
            for (JsonNode image : images) values.add(new FrozenImage(
                image.path("sortOrder").asInt(), imageUrlService.accessUrl(image.path("sourceUrl").asText(null),
                    image.path("storagePath").asText(null)), image.path("altText").asText(null)));
            return List.copyOf(values);
        } catch (RuntimeException exception) {
            if (exception instanceof LearningHistoryException history) throw history;
            throw readFailure("冻结题目图片读取失败", exception);
        }
    }

    private List<HistoryExamDetailVo.OptionVo> examOptions(JsonNode presentation) {
        JsonNode options = presentation.path("options");
        if (!options.isArray()) return List.of();
        return options.valueStream().map(x -> new HistoryExamDetailVo.OptionVo(
            x.path("label").asText(), x.path("content").asText())).toList();
    }

    private JsonNode checkedSnapshot(String raw, String message) {
        JsonNode value = tree(raw);
        if (!value.isObject() || !value.has("schema_version")) throw readFailure(message, null);
        return value;
    }

    private JsonNode checkedObject(String raw, String message) {
        JsonNode value = tree(raw);
        if (!value.isObject()) throw readFailure(message, null);
        return value;
    }

    private List<String> labels(JsonNode value, String message) {
        if (!value.isArray()) throw readFailure(message, null);
        List<String> labels = value.valueStream().map(JsonNode::asText).toList();
        if (labels.stream().anyMatch(String::isBlank)) throw readFailure(message, null);
        return labels;
    }

    private BigDecimal decimalValue(JsonNode value, String message) {
        try {
            return new BigDecimal(value.asText());
        } catch (RuntimeException exception) {
            throw readFailure(message, exception);
        }
    }

    private String practiceSessionType(String type) {
        return "KNOWLEDGE_PRACTICE".equals(type) ? "self_practice" : "past_paper_practice";
    }

    private String practiceType(String type) {
        return "self_practice".equals(type) ? "KNOWLEDGE_PRACTICE" : "PAST_PAPER_PRACTICE";
    }

    private String answerSummary(HistoryAttemptRow row, Snapshot snapshot) {
        if (row.isSkipped()) return "未作答";
        if (!"CHOICE".equals(snapshot.type())) return row.getAnswerData() == null ? "答案已不再保留，评分结果仍有效" : "已提交答案";
        try {
            JsonNode value = tree(row.getAnswerData()).path("value");
            List<String> labels = value.isArray() ? value.valueStream().map(JsonNode::asText).filter(x -> !x.isBlank()).toList() : List.of();
            return labels.isEmpty() ? "未作答" : "选择 " + String.join("、", labels);
        } catch (RuntimeException exception) {
            throw readFailure("历史答案摘要读取失败", exception);
        }
    }

    private long goal(long userId, String value) {
        if (value == null || value.isBlank()) {
            Long active = mapper.selectActiveGoal(userId);
            if (active == null) throw failure(422, "HISTORY_GOAL_REQUIRED", "请选择可查看的学习目标");
            return active;
        }
        long goalId = requiredId(value, "goalId");
        if (!mapper.existsOwnedGoal(userId, goalId)) throw failure(404, "HISTORY_GOAL_NOT_FOUND", "学习目标不存在");
        return goalId;
    }

    private Page page(Integer pageNum, Integer pageSize) {
        int number = pageNum == null ? 1 : pageNum, size = pageSize == null ? 10 : pageSize;
        if (number < 1 || number > 10000) throw invalid("pageNum", "OUT_OF_RANGE", "页码超出范围");
        if (size < 1 || size > 100) throw invalid("pageSize", "OUT_OF_RANGE", "每页数量超出范围");
        return new Page((number - 1) * size, size);
    }

    private String keyword(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 100) throw invalid("keyword", "OUT_OF_RANGE", "关键字超出范围");
        return normalized;
    }

    private String enumeration(String value, Set<String> allowed, String field) {
        if (value == null || value.isBlank()) return null;
        String normalized = upper(value);
        if (!allowed.contains(normalized)) throw invalid(field, "INVALID_FORMAT", "枚举值不合法");
        return normalized;
    }

    private Range range(LocalDate from, LocalDate to, String fromField, String toField) {
        if (from != null && to != null && from.isAfter(to)) throw invalid(fromField, "OUT_OF_RANGE", "日期范围不合法");
        return new Range(from == null ? null : from.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime(),
            to == null ? null : to.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime());
    }

    private Long optionalId(String value, String field) {
        return value == null || value.isBlank() ? null : requiredId(value, field);
    }
    private long requiredId(String value, String field) {
        try { long parsed = Long.parseLong(value); if (parsed <= 0) throw new NumberFormatException(); return parsed; }
        catch (RuntimeException exception) { throw invalid(field, "INVALID_FORMAT", "ID格式不正确"); }
    }
    private JsonNode tree(String raw) { try { return jsonMapper.readTree(raw == null ? "{}" : raw); } catch (Exception e) { throw readFailure("JSON快照解析失败", e); } }
    private String preview(String stem) { return HtmlUtil.cleanHtmlTag(stem).replaceAll("\\s+", " ").trim().codePoints().limit(100).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString(); }
    private String decimal(BigDecimal value) { return value == null ? null : value.stripTrailingZeros().toPlainString(); }
    private String id(long value) { return String.valueOf(value); }
    private String upper(String value) { return value == null ? null : value.toUpperCase(Locale.ROOT); }
    private String source(String value) { return switch (value) {
        case "initial_diagnosis" -> "INITIAL_DIAGNOSIS";
        case "daily_task", "task_test" -> "DAILY_TASK";
        case "self_practice" -> "SELF_PRACTICE";
        case "simulation" -> "SIMULATION";
        case "correction" -> "CORRECTION";
        case "independent_verification" -> "INDEPENDENT_VERIFICATION";
        case "simulation_verification" -> "SIMULATION_VERIFICATION";
        default -> "OTHER";
    }; }

    private LearningHistoryException invalid(String field, String code, String message) {
        return new LearningHistoryException(400, "HISTORY_REQUEST_INVALID", message, false,
            List.of(new HistoryErrorVo.FieldErrorVo(field, code, message)), null, null);
    }
    private LearningHistoryException failure(int status, String code, String message) {
        return new LearningHistoryException(status, code, message);
    }
    private LearningHistoryException readFailure(String message, Throwable cause) {
        Throwable internalCause = cause == null ? new IllegalStateException(message) : new IllegalStateException(message, cause);
        return new LearningHistoryException(500, "HISTORY_READ_FAILED", "学习记录读取失败", false,
            List.of(), null, internalCause);
    }

    private record Page(int offset, int size) {}
    private record Range(OffsetDateTime from, OffsetDateTime to) {}
    private record Lookup(String id, String name) {}
    private record FrozenImage(int sortOrder, String url, String altText) {}
    private record Snapshot(String type, String preview, JsonNode presentation, JsonNode knowledge) {}
    private record TaskSnapshot(String title, String knowledgeId, String knowledgeName) {}
    private record PracticeCounts(int questionCount, int answeredCount, int correctCount,
                                  int incorrectCount, int unansweredCount) { }
    private record GradingItem(String code, String description, BigDecimal weight, BigDecimal scoreRate) { }
    private record GradingProjection(String feedback, List<GradingItem> items) {
        private static GradingProjection empty() { return new GradingProjection(null, List.of()); }
    }
}
