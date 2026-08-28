package org.dromara.certmuse.learning.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.learning.domain.LearningGoalCertificationRow;
import org.dromara.certmuse.learning.domain.LearningGoalBatchRow;
import org.dromara.certmuse.learning.domain.LearningGoalIdempotencyRow;
import org.dromara.certmuse.learning.domain.LearningGoalRow;
import org.dromara.certmuse.learning.domain.bo.CreateLearningGoalBo;
import org.dromara.certmuse.learning.domain.bo.SwitchLearningGoalBo;
import org.dromara.certmuse.learning.domain.vo.ContractErrorVo;
import org.dromara.certmuse.learning.domain.vo.CreateLearningGoalResultVo;
import org.dromara.certmuse.learning.domain.vo.LearningGoalOptionsVo;
import org.dromara.certmuse.learning.domain.vo.GoalSwitchOptionsVo;
import org.dromara.certmuse.learning.domain.vo.GoalSwitchErrorVo;
import org.dromara.certmuse.learning.domain.vo.SwitchLearningGoalResultVo;
import org.dromara.certmuse.learning.mapper.LearningGoalMapper;
import org.dromara.certmuse.learning.service.LearningGoalService;
import org.dromara.certmuse.learning.support.LearningGoalException;
import org.dromara.certmuse.learning.support.GoalSwitchException;
import org.dromara.certmuse.learning.support.LearningJsonSchema;
import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Transactional implementation of the learner's first learning-goal use case. */
@Service
@RequiredArgsConstructor
@Slf4j
public class LearningGoalServiceImpl implements LearningGoalService {
    private static final String ACTION = "LEARNING_GOAL_CREATE";
    private static final int DAILY_MINUTES_MIN = 15;
    private static final int DAILY_MINUTES_MAX = 300;
    private static final int DAILY_MINUTES_DEFAULT = 30;
    private static final List<Integer> EXAM_MONTHS = List.of(5, 11);

    private final LearningGoalMapper mapper;
    private final JsonMapper jsonMapper;
    private final Clock learningClock;

    @Override
    public LearningGoalOptionsVo options(long userId) {
        rejectIfCurrentGoalExists(userId);
        LocalDate today = LocalDate.now(learningClock);
        List<LearningGoalCertificationRow> certifications = mapper.selectSelectableCertifications(today);
        List<LearningGoalOptionsVo.ExamYearOptionVo> years = examYears(today);
        LearningGoalOptionsVo.DefaultsVo defaults = certifications.isEmpty() ? null : defaults(certifications.getFirst(), years);
        return new LearningGoalOptionsVo(
            OffsetDateTime.now(learningClock).toString(),
            learningClock.getZone().getId(),
            certifications.stream().map(row -> new LearningGoalOptionsVo.CertificationOptionVo(
                Long.toString(row.certificationId()), row.certificationCode(), row.certificationName()
            )).toList(),
            years,
            new LearningGoalOptionsVo.DailyMinutesRuleVo(DAILY_MINUTES_MIN, DAILY_MINUTES_MAX, DAILY_MINUTES_DEFAULT),
            defaults
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateLearningGoalResultVo create(long userId, String requestId, CreateLearningGoalBo command) {
        String normalizedRequestId = requestId(requestId);
        CreateInput input = input(command);
        String payloadHash = hash(payload(input));
        LearningGoalIdempotencyRow existing = mapper.selectIdempotency(ACTION, normalizedRequestId);
        if (existing != null) return replay(existing, payloadHash, userId);

        LocalDate today = LocalDate.now(learningClock);
        validateTargetBatch(input.targetExamYear(), input.targetExamMonth(), today);
        validateDailyMinutes(input.dailyMinutes());
        LearningGoalCertificationRow certification = mapper.selectEnabledCertificationWithSyllabus(input.certificationId(), today);
        if (certification == null) {
            String certificationStatus = mapper.selectCertificationStatus(input.certificationId());
            if (!"0".equals(certificationStatus)) throw certificationUnavailable(input.certificationId());
            throw syllabusNotReady();
        }

        long goalId = IdUtil.getSnowflakeNextId();
        long idemId = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(idemId, ACTION, normalizedRequestId, payloadHash, goalId,
            OffsetDateTime.now(learningClock).plusDays(1)) != 1) {
            LearningGoalIdempotencyRow concurrent = mapper.selectIdempotency(ACTION, normalizedRequestId);
            if (concurrent != null) return replay(concurrent, payloadHash, userId);
            throw failure("学习目标创建失败");
        }

        if (mapper.lockCurrentGoalId(userId) != null) throw alreadyExists();
        try {
            LearningGoalBatchRow batch = mapper.selectBatchSnapshot(certification.certificationId(), input.targetExamYear(), input.targetExamMonth(), today);
            if (batch == null) batch = new LearningGoalBatchRow("estimated", null);
            if (mapper.insertGoal(goalId, userId, certification.certificationId(), certification.syllabusVersionId(),
                input.targetExamYear(), input.targetExamMonth(), input.dailyMinutes(), batch.examBatchType(), batch.targetExamDate()) != 1) {
                throw failure("学习目标创建失败");
            }
            LearningGoalRow goal = requireGoal(mapper.selectGoal(goalId));
            if (mapper.insertGoalChange(IdUtil.getSnowflakeNextId(), goalId, userId, snapshot(goal), normalizedRequestId, userId) != 1) {
                throw failure("学习目标变更记录创建失败");
            }
            CreateLearningGoalResultVo result = result(goal);
            if (mapper.completeIdempotency(idemId, response(result)) != 1) throw failure("学习目标幂等结果保存失败");
            return result;
        } catch (DataIntegrityViolationException exception) {
            throw alreadyExists();
        }
    }

    private void rejectIfCurrentGoalExists(long userId) {
        if (mapper.selectCurrentGoalId(userId) != null) throw alreadyExists();
    }

    private LearningGoalOptionsVo.DefaultsVo defaults(LearningGoalCertificationRow certification,
                                                       List<LearningGoalOptionsVo.ExamYearOptionVo> years) {
        return years.stream().filter(LearningGoalOptionsVo.ExamYearOptionVo::selectable).findFirst()
            .map(year -> new LearningGoalOptionsVo.DefaultsVo(
                Long.toString(certification.certificationId()), year.year(), year.months().getFirst()
            )).orElse(null);
    }

    private List<LearningGoalOptionsVo.ExamYearOptionVo> examYears(LocalDate today) {
        return java.util.stream.IntStream.rangeClosed(today.getYear(), today.getYear() + 4)
            .mapToObj(year -> {
                List<Integer> months = year == today.getYear()
                    ? EXAM_MONTHS.stream().filter(month -> month > today.getMonthValue()).toList()
                    : EXAM_MONTHS;
                return new LearningGoalOptionsVo.ExamYearOptionVo(year, months, !months.isEmpty());
            }).toList();
    }

    private CreateInput input(CreateLearningGoalBo command) {
        if (command == null) throw invalid("请求参数不能为空", List.of());
        try {
            long certificationId = Long.parseLong(command.getCertificationId());
            if (certificationId <= 0 || command.getTargetExamYear() == null || command.getTargetExamMonth() == null
                || command.getDailyMinutes() == null) throw new NumberFormatException();
            return new CreateInput(certificationId, command.getTargetExamYear(), command.getTargetExamMonth(), command.getDailyMinutes());
        } catch (RuntimeException exception) {
            throw invalid("请求参数格式不正确", List.of());
        }
    }

    private void validateTargetBatch(int year, int month, LocalDate today) {
        if (!EXAM_MONTHS.contains(month) || year < today.getYear() || year > today.getYear() + 4
            || !YearMonth.of(year, month).isAfter(YearMonth.from(today))) {
            throw new LearningGoalException(400, "TARGET_EXAM_BATCH_INVALID", "目标考试批次不合法", false, null,
                List.of(new ContractErrorVo.FieldErrorVo("targetExamMonth", "OUT_OF_RANGE", "目标考试批次必须在可选范围内")));
        }
    }

    private void validateDailyMinutes(int minutes) {
        if (minutes < DAILY_MINUTES_MIN || minutes > DAILY_MINUTES_MAX) {
            throw new LearningGoalException(400, "DAILY_MINUTES_OUT_OF_RANGE", "每日学习时长不在允许范围内", false, null,
                List.of(new ContractErrorVo.FieldErrorVo("dailyMinutes", "OUT_OF_RANGE", "每日学习时长必须为15至300分钟")));
        }
    }

    private CreateLearningGoalResultVo replay(LearningGoalIdempotencyRow record, String payloadHash, long userId) {
        if (!constantTimeEquals(record.payloadHash(), payloadHash) || !"succeeded".equals(record.status())
            || record.resourceId() == null || record.goalOwnerId() == null || record.goalOwnerId() != userId) {
            throw conflict("GOAL_IDEMPOTENCY_CONFLICT", "请求号已被使用");
        }
        try {
            JsonNode root = jsonMapper.readTree(record.responseBody());
            return jsonMapper.treeToValue(root.path("data"), CreateLearningGoalResultVo.class);
        } catch (Exception exception) {
            throw failure("读取幂等结果失败", exception);
        }
    }

    private CreateLearningGoalResultVo result(LearningGoalRow goal) {
        return new CreateLearningGoalResultVo(
            new CreateLearningGoalResultVo.GoalVo(
                Long.toString(goal.id()), Long.toString(goal.certificationId()), goal.certificationName(),
                Long.toString(goal.syllabusVersionId()), goal.syllabusVersionName(), goal.targetExamYear(),
                goal.targetExamMonth(), goal.dailyMinutes(), "ACTIVE", goal.rowVersion()
            ),
            "START_DIAGNOSTIC"
        );
    }

    private String snapshot(LearningGoalRow goal) {
        Map<String, Object> body = VersionedJsonDocumentFactory.flatFields(
            LearningJsonSchema.USER_GOAL_SNAPSHOT, Map.of());
        Map<String, Object> goalSnapshot = new LinkedHashMap<>();
        goalSnapshot.put("id", Long.toString(goal.id()));
        goalSnapshot.put("certificationId", Long.toString(goal.certificationId()));
        goalSnapshot.put("syllabusVersionId", Long.toString(goal.syllabusVersionId()));
        goalSnapshot.put("targetExamYear", goal.targetExamYear());
        goalSnapshot.put("targetExamMonth", goal.targetExamMonth());
        goalSnapshot.put("dailyMinutes", goal.dailyMinutes());
        goalSnapshot.put("status", goal.status().toUpperCase());
        goalSnapshot.put("version", goal.rowVersion());
        goalSnapshot.put("examBatchType", goal.examBatchType());
        goalSnapshot.put("targetExamDate", goal.targetExamDate() == null ? null : goal.targetExamDate().toString());
        body.put("goal", goalSnapshot);
        return json(body);
    }

    private Map<String, Object> payload(CreateInput input) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("certificationId", input.certificationId());
        payload.put("targetExamYear", input.targetExamYear());
        payload.put("targetExamMonth", input.targetExamMonth());
        payload.put("dailyMinutes", input.dailyMinutes());
        return payload;
    }

    private String response(CreateLearningGoalResultVo result) {
        return VersionedJsonDocumentFactory.json(jsonMapper, LearningJsonSchema.LEARNING_GOAL_RESPONSE,
            Map.of("data", result));
    }

    private String response(SwitchLearningGoalResultVo result) {
        return VersionedJsonDocumentFactory.json(jsonMapper, LearningJsonSchema.LEARNING_GOAL_RESPONSE,
            Map.of("data", result));
    }

    private String hash(Object value) {
        return DigestUtil.sha256Hex(json(value));
    }

    private String json(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw failure("学习目标数据序列化失败", exception);
        }
    }

    private String requestId(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (Exception exception) {
            throw invalid("X-Request-Id必须是UUID", List.of(
                new ContractErrorVo.FieldErrorVo("X-Request-Id", "INVALID_FORMAT", "请求头必须为UUID")
            ));
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return left != null && right != null && java.security.MessageDigest.isEqual(
            left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private LearningGoalRow requireGoal(LearningGoalRow goal) {
        if (goal == null || goal.targetExamYear() == null || goal.targetExamMonth() == null) throw failure("学习目标读取失败");
        return goal;
    }

    private LearningGoalException certificationUnavailable(long certificationId) {
        return new LearningGoalException(422, "CERTIFICATION_UNAVAILABLE", "资格不可用于创建学习目标", false, null,
            List.of(new ContractErrorVo.FieldErrorVo("certificationId", "UNSUPPORTED", "资格未启用或不存在")));
    }

    private LearningGoalException syllabusNotReady() {
        return new LearningGoalException(422, "SYLLABUS_VERSION_NOT_READY", "资格暂无已生效考纲版本", true, null, List.of());
    }

    private LearningGoalException alreadyExists() {
        return conflict("GOAL_ALREADY_EXISTS", "当前用户已有有效学习目标");
    }

    private LearningGoalException conflict(String code, String message) {
        return new LearningGoalException(409, code, message);
    }

    private LearningGoalException invalid(String message, List<ContractErrorVo.FieldErrorVo> fields) {
        return new LearningGoalException(400, "GOAL_REQUEST_INVALID", message, false, null, fields);
    }

    private LearningGoalException failure(String message) {
        return new LearningGoalException(500, "LEARNING_GOAL_CREATE_FAILED", message, true, null, List.of());
    }

    private LearningGoalException failure(String message, Throwable cause) {
        return new LearningGoalException(
            500, "LEARNING_GOAL_CREATE_FAILED", message, true, null, List.of(), cause);
    }

    private record CreateInput(long certificationId, int targetExamYear, int targetExamMonth, int dailyMinutes) {
    }

    @Override
    public GoalSwitchOptionsVo switchOptions(long userId) {
        LearningGoalRow goal = mapper.selectActiveGoal(userId);
        if (goal == null) throw switchConflict("LEARNING_GOAL_NOT_ACTIVE", "当前没有进行中的学习目标", false);
        LocalDate today = LocalDate.now(learningClock);
        enforceSwitchAllowed(goal, today);
        List<LearningGoalCertificationRow> rows = mapper.selectSelectableCertifications(today);
        return new GoalSwitchOptionsVo(OffsetDateTime.now(learningClock).toString(), learningClock.getZone().getId(),
            switchGoalVo(goal), mapper.selectPendingSessions(goal.id()).stream().map(s -> new GoalSwitchOptionsVo.SessionImpactVo(s.sessionType(), s.title(), s.status().toUpperCase())).toList(),
            rows.stream().filter(c -> c.certificationId() != goal.certificationId()).map(c ->
                new GoalSwitchOptionsVo.CertificationOptionVo(Long.toString(c.certificationId()), c.certificationCode(),
                    c.certificationName(), switchBatches(c.certificationId(), today))).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SwitchLearningGoalResultVo switchGoal(long userId, String requestId, SwitchLearningGoalBo command) {
        String normalized = switchRequestId(requestId);
        SwitchInput input = switchInput(command);
        String payloadHash = hash(switchPayload(input));
        LearningGoalIdempotencyRow existing = mapper.selectIdempotency("GOAL_SWITCH", normalized);
        if (existing != null) return replaySwitch(existing, payloadHash, userId);
        LearningGoalRow old = mapper.lockActiveGoal(userId);
        if (old == null) throw switchConflict("LEARNING_GOAL_NOT_ACTIVE", "当前没有进行中的学习目标", false);
        LocalDate today = LocalDate.now(learningClock);
        enforceSwitchAllowed(old, today);
        if (old.rowVersion() != input.expectedCurrentGoalVersion()) {
            throw switchConflict("GOAL_VERSION_CONFLICT", "当前学习目标已发生变化", true);
        }
        if (input.certificationId() == old.certificationId()) {
            throw switchFailure(422, "GOAL_SWITCH_SAME_CERTIFICATION", "此处只支持切换其他资格", false, (GoalSwitchErrorVo.DetailsVo) null);
        }
        LearningGoalCertificationRow certification = mapper.selectEnabledCertificationWithSyllabus(input.certificationId(), today);
        if (certification == null) throw switchFailure(422, "CERTIFICATION_UNAVAILABLE", "资格不可用于切换学习目标", false,
            new ContractErrorVo.FieldErrorVo("certificationId", "UNSUPPORTED", "资格未启用或不存在"));
        LearningGoalBatchRow batch = switchBatch(input, today);
        var pending=mapper.lockPendingSessions(old.id());
        ensureKnownSessionTypes(pending);
        if (!pending.isEmpty() && !input.confirmAbandonInProgress()) {
            throw switchFailure(409, "GOAL_SWITCH_CONFIRM_REQUIRED", "请确认放弃未交卷会话", false,
                new GoalSwitchErrorVo.DetailsVo(pending.size()));
        }
        long id=IdUtil.getSnowflakeNextId(), idemId=IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(idemId, "GOAL_SWITCH", normalized, payloadHash, id,
            OffsetDateTime.now(learningClock).plusDays(1)) != 1) {
            return replaySwitch(mapper.selectIdempotency("GOAL_SWITCH", normalized), payloadHash, userId);
        }
        String oldBefore = snapshot(old);
        int cancelled = mapper.cancelPendingSessions(old.id(), userId);
        if (cancelled != pending.size() || mapper.pauseGoal(old.id(), old.rowVersion()) != 1) {
            throw switchUnexpected("学习目标切换失败", null);
        }
        LearningGoalRow paused=mapper.selectGoal(old.id());
        if (paused == null || mapper.insertGoalSwitchChange(IdUtil.getSnowflakeNextId(), old.id(), userId, "switched_out",
            oldBefore, snapshot(paused), "switch_to_other_certification", normalized, userId) != 1) {
            throw switchUnexpected("学习目标审计记录创建失败", null);
        }
        if (mapper.insertGoal(id, userId, input.certificationId(), certification.syllabusVersionId(), input.targetExamYear(),
            input.targetExamMonth(), old.dailyMinutes(), batch.examBatchType(), batch.targetExamDate()) != 1) {
            throw switchUnexpected("学习目标创建失败", null);
        }
        LearningGoalRow current=requireGoal(mapper.selectGoal(id));
        if (mapper.insertGoalSwitchChange(IdUtil.getSnowflakeNextId(), id, userId, "switched_in", null, snapshot(current),
            "switch_from_other_certification", normalized, userId) != 1) {
            throw switchUnexpected("学习目标审计记录创建失败", null);
        }
        SwitchLearningGoalResultVo result=new SwitchLearningGoalResultVo(new SwitchLearningGoalResultVo.PreviousGoalVo(Long.toString(old.id()),"PAUSED",old.rowVersion()+1),switchGoalVo(current),cancelled,"ENTER_HOME");
        if (mapper.completeIdempotency(idemId, response(result)) != 1) {
            throw switchUnexpected("学习目标幂等结果保存失败", null);
        }
        log.info("Learning goal switched: userId={}, oldGoalId={}, newGoalId={}, oldCertificationId={}, newCertificationId={}, requestId={}, oldVersion={}, abandonedSessionCount={}",
            userId, old.id(), current.id(), old.certificationId(), current.certificationId(), normalized, old.rowVersion(), cancelled);
        return result;
    }

    private GoalSwitchOptionsVo.CurrentGoalVo switchGoalVo(LearningGoalRow goal) {
        return new GoalSwitchOptionsVo.CurrentGoalVo(Long.toString(goal.id()),Long.toString(goal.certificationId()),goal.certificationName(),Long.toString(goal.syllabusVersionId()),goal.syllabusVersionName(),goal.targetExamYear(),goal.targetExamMonth(),goal.targetExamDate()==null?null:goal.targetExamDate().toString(),goal.examBatchType().toUpperCase(),goal.status().toUpperCase(),goal.rowVersion());
    }
    private String switchRequestId(String value) {
        if (value == null || value.isBlank() || value.length() > 100) {
            throw switchFailure(400, "GOAL_SWITCH_REQUEST_INVALID", "X-Request-Id格式不正确", false,
                new ContractErrorVo.FieldErrorVo("X-Request-Id", "INVALID_FORMAT", "请求头长度必须为1至100"));
        }
        return value;
    }
    private SwitchLearningGoalResultVo replaySwitch(LearningGoalIdempotencyRow record,String payloadHash,long userId) {
        if (record == null || !constantTimeEquals(record.payloadHash(), payloadHash) || !"succeeded".equals(record.status())
            || record.goalOwnerId() == null || record.goalOwnerId() != userId) {
            throw switchConflict("GOAL_SWITCH_IDEMPOTENCY_CONFLICT", "请求号已被使用", false);
        }
        try { return jsonMapper.treeToValue(jsonMapper.readTree(record.responseBody()).path("data"),SwitchLearningGoalResultVo.class); }
        catch (Exception exception) { throw switchUnexpected("读取幂等结果失败", exception); }
    }

    private List<GoalSwitchOptionsVo.ExamBatchVo> switchBatches(long certificationId, LocalDate today) {
        List<LearningGoalBatchRow> official = mapper.selectFutureOfficialBatches(certificationId, today);
        if (!official.isEmpty()) {
            return official.stream().map(batch -> new GoalSwitchOptionsVo.ExamBatchVo(batch.targetExamDate().getYear(),
                batch.targetExamDate().getMonthValue(), "OFFICIAL", batch.targetExamDate().toString())).toList();
        }
        YearMonth estimate = nextEstimatedBatch(today);
        return List.of(new GoalSwitchOptionsVo.ExamBatchVo(estimate.getYear(), estimate.getMonthValue(), "ESTIMATED", null));
    }

    private LearningGoalBatchRow switchBatch(SwitchInput input, LocalDate today) {
        if (!EXAM_MONTHS.contains(input.targetExamMonth()) || input.targetExamYear() < today.getYear()
            || input.targetExamYear() > today.getYear() + 4) {
            throw invalidSwitchBatch();
        }
        LearningGoalBatchRow batch = mapper.selectBatchSnapshot(input.certificationId(), input.targetExamYear(),
            input.targetExamMonth(), today);
        if (batch == null) throw invalidSwitchBatch();
        if ("official".equals(batch.examBatchType())) return batch;
        if (!YearMonth.of(input.targetExamYear(), input.targetExamMonth()).isAfter(YearMonth.from(today))) {
            throw invalidSwitchBatch();
        }
        return batch;
    }

    private void enforceSwitchAllowed(LearningGoalRow goal, LocalDate today) {
        if (!"0".equals(mapper.selectCertificationStatus(goal.certificationId()))) {
            throw switchFailure(422, "GOAL_SWITCH_NOT_ALLOWED", "当前资格已停止维护", false, (GoalSwitchErrorVo.DetailsVo) null);
        }
        LocalDate endDate = switchEndDate(goal);
        if (endDate != null && today.isAfter(endDate)) {
            throw switchFailure(422, "GOAL_RESULT_CONFIRMATION_REQUIRED", "当前批次已结束，需先确认考试结果", false, (GoalSwitchErrorVo.DetailsVo) null);
        }
    }

    private LocalDate switchEndDate(LearningGoalRow goal) {
        if ("official".equals(goal.examBatchType()) && goal.targetExamDate() != null) return goal.targetExamDate();
        if ("legacy_unknown".equals(goal.examBatchType())) {
            LocalDate official = mapper.selectHistoricalOfficialExamDate(goal.certificationId(), goal.targetExamYear(), goal.targetExamMonth());
            if (official != null) return official;
        }
        return YearMonth.of(goal.targetExamYear(), goal.targetExamMonth()).atEndOfMonth();
    }

    private YearMonth nextEstimatedBatch(LocalDate today) {
        for (int year = today.getYear(); year <= today.getYear() + 4; year++) {
            for (int month : EXAM_MONTHS) {
                YearMonth candidate = YearMonth.of(year, month);
                if (candidate.isAfter(YearMonth.from(today))) return candidate;
            }
        }
        throw switchUnexpected("没有可用预计考试批次", null);
    }

    private void ensureKnownSessionTypes(List<?> sessions) {
        // Database CHECK is the source of truth; a non-empty projection is intentionally fail-closed here.
        for (Object session : sessions) {
            String type = ((org.dromara.certmuse.learning.domain.LearningGoalSwitchSessionRow) session).sessionType();
            if (!Set.of("initial_diagnosis", "daily_task", "task_test", "self_practice", "simulation", "correction",
                "independent_verification", "simulation_verification").contains(type)) {
                throw switchUnexpected("学习会话类型不可识别", null);
            }
        }
    }

    private SwitchInput switchInput(SwitchLearningGoalBo command) {
        try {
            if (command == null || command.getCertificationId() == null || command.getTargetExamYear() == null
                || command.getTargetExamMonth() == null || command.getExpectedCurrentGoalVersion() == null
                || command.getConfirmAbandonInProgress() == null) throw new IllegalArgumentException();
            long certificationId = Long.parseLong(command.getCertificationId());
            if (certificationId <= 0 || command.getExpectedCurrentGoalVersion() < 0) throw new IllegalArgumentException();
            return new SwitchInput(certificationId, command.getCertificationId(), command.getTargetExamYear(), command.getTargetExamMonth(),
                command.getExpectedCurrentGoalVersion(), command.getConfirmAbandonInProgress());
        } catch (RuntimeException exception) {
            throw switchFailure(400, "GOAL_SWITCH_REQUEST_INVALID", "请求参数格式不正确", false, (GoalSwitchErrorVo.DetailsVo) null);
        }
    }

    private Map<String, Object> switchPayload(SwitchInput input) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("certificationId", input.certificationIdText());
        payload.put("targetExamYear", input.targetExamYear());
        payload.put("targetExamMonth", input.targetExamMonth());
        payload.put("expectedCurrentGoalVersion", input.expectedCurrentGoalVersion());
        payload.put("confirmAbandonInProgress", input.confirmAbandonInProgress());
        return payload;
    }

    private GoalSwitchException invalidSwitchBatch() {
        return switchFailure(400, "TARGET_EXAM_BATCH_INVALID", "目标考试批次不合法", false,
            new ContractErrorVo.FieldErrorVo("targetExamMonth", "OUT_OF_RANGE", "目标考试批次必须在可选范围内"));
    }

    private GoalSwitchException switchConflict(String code, String message, boolean retryable) {
        return switchFailure(409, code, message, retryable, (GoalSwitchErrorVo.DetailsVo) null);
    }

    private GoalSwitchException switchFailure(int status, String code, String message, boolean retryable,
                                              ContractErrorVo.FieldErrorVo fieldError) {
        return new GoalSwitchException(status, code, message, retryable,
            fieldError == null ? List.of() : List.of(fieldError), null, null);
    }

    private GoalSwitchException switchFailure(int status, String code, String message, boolean retryable,
                                              GoalSwitchErrorVo.DetailsVo details) {
        return new GoalSwitchException(status, code, message, retryable, List.of(), details, null);
    }

    private GoalSwitchException switchUnexpected(String message, Throwable cause) {
        return new GoalSwitchException(500, "LEARNING_GOAL_SWITCH_FAILED", "系统暂时无法处理学习目标切换", true,
            List.of(), null, cause == null ? new IllegalStateException(message) : cause);
    }

    private record SwitchInput(long certificationId, String certificationIdText, int targetExamYear,
                               int targetExamMonth, long expectedCurrentGoalVersion, boolean confirmAbandonInProgress) {
    }
}
