package org.dromara.certmuse.assessment.service.impl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.assessment.domain.SimulationGoalRow;
import org.dromara.certmuse.assessment.domain.SimulationOptionRow;
import org.dromara.certmuse.assessment.domain.SimulationPaperRow;
import org.dromara.certmuse.assessment.domain.SimulationPreviewQuestionRow;
import org.dromara.certmuse.assessment.domain.bo.SimulationQueryBo;
import org.dromara.certmuse.assessment.domain.bo.StartSimulationSessionBo;
import org.dromara.certmuse.assessment.domain.vo.SimulationAccessVo;
import org.dromara.certmuse.assessment.domain.vo.SimulationDetailVo;
import org.dromara.certmuse.assessment.domain.vo.SimulationErrorVo;
import org.dromara.certmuse.assessment.domain.vo.SimulationListItemVo;
import org.dromara.certmuse.assessment.domain.vo.SimulationPreviewQuestionVo;
import org.dromara.certmuse.assessment.domain.vo.SimulationPreviewVo;
import org.dromara.certmuse.assessment.domain.vo.SimulationSetupVo;
import org.dromara.certmuse.assessment.domain.vo.SimulationSubjectVo;
import org.dromara.certmuse.assessment.domain.vo.StartSimulationSessionVo;
import org.dromara.certmuse.assessment.mapper.SimulationMapper;
import org.dromara.certmuse.assessment.service.SimulationService;
import org.dromara.certmuse.assessment.service.FormalExamEngine;
import org.dromara.certmuse.assessment.domain.bo.FormalExamDraftBo;
import org.dromara.certmuse.assessment.domain.bo.FormalExamFinishBo;
import org.dromara.certmuse.assessment.domain.bo.FormalExamPauseBo;
import org.dromara.certmuse.assessment.domain.bo.FormalExamTimerEventBo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamFinishCheckVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamFinishVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamItemVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamResultVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamSessionVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamStatusVo;
import org.dromara.certmuse.assessment.domain.vo.FormalExamTimerEventVo;
import org.dromara.certmuse.assessment.support.SimulationException;
import org.dromara.common.core.domain.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** U13 implementation with real published-paper reads and a closed formal-exam gate. */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class SimulationServiceImpl implements SimulationService {
    private static final String TIMEZONE = "Asia/Shanghai";
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private static final List<String> RULES = List.of(
        "开始后按正式考试时长计时，离开页面计时继续。",
        "提前交卷时会提示未答题数量；确认后未答题按正式评分规则处理。",
        "同一试卷允许重考，每次报告单独保留。"
    );

    private final SimulationMapper mapper;
    private final Clock clock;
    private final FormalExamEngine formalExamEngine;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SimulationSetupVo setup(long userId) {
        try {
            SimulationGoalRow goal = mapper.selectCurrentGoal(userId);
            Map<Long, CertificationOptions> grouped = new LinkedHashMap<>();
            for (SimulationOptionRow row : mapper.selectSetupOptions()) {
                CertificationOptions options = grouped.computeIfAbsent(row.getCertificationId(),
                    ignored -> new CertificationOptions(row.getCertificationId(), row.getCertificationName()));
                options.syllabuses().add(new SimulationSetupVo.SyllabusVersionVo(
                    String.valueOf(row.getSyllabusVersionId()), row.getSyllabusVersionName()));
            }
            List<SimulationSetupVo.CertificationVo> certifications = grouped.values().stream()
                .map(CertificationOptions::toVo).toList();
            return new SimulationSetupVo(OffsetDateTime.now(clock), TIMEZONE, goalVo(goal), certifications);
        } catch (SimulationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw system("模拟试卷初始化数据读取失败", exception);
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<SimulationListItemVo> list(long userId, SimulationQueryBo query) {
        QueryValues values = normalize(query);
        try {
            long total = mapper.countPublishedSimulations(
                values.certificationId(), values.syllabusVersionId(), values.keyword());
            if (total == 0) {
                return PageResult.build(List.of(), 0);
            }
            SimulationGoalRow goal = mapper.selectCurrentGoal(userId);
            List<SimulationListItemVo> rows = mapper.selectPublishedSimulations(
                    userId, values.certificationId(), values.syllabusVersionId(), values.keyword(),
                    values.pageSize(), (long) (values.pageNum() - 1) * values.pageSize())
                .stream().map(row -> listVo(row, goal)).toList();
            return PageResult.build(rows, total);
        } catch (SimulationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw system("模拟试卷列表读取失败", exception);
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SimulationDetailVo detail(long userId, String collectionId) {
        long id = requiredId(collectionId, "collectionId");
        try {
            SimulationPaperRow row = mapper.selectPublishedSimulation(userId, id);
            if (row == null) {
                throw new SimulationException(404, "SIMULATION_NOT_FOUND", "模拟试卷不存在", false);
            }
            return detailVo(row, mapper.selectCurrentGoal(userId));
        } catch (SimulationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw system("模拟考试说明读取失败", exception);
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SimulationPreviewVo preview(long userId, String collectionId) {
        long id = requiredId(collectionId, "collectionId");
        try {
            SimulationPaperRow paper = mapper.selectPublishedSimulation(userId, id);
            if (paper == null) {
                throw new SimulationException(404, "SIMULATION_NOT_FOUND", "模拟试卷不存在", false);
            }
            List<SimulationPreviewQuestionVo> questions = mapper.selectPublishedSimulationPreviewQuestions(id).stream()
                .map(this::previewQuestionVo).toList();
            return new SimulationPreviewVo(String.valueOf(paper.getCollectionId()), String.valueOf(paper.getRevisionId()),
                paper.getCollectionName(), questions);
        } catch (SimulationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw system("模拟试卷题干读取失败", exception);
        }
    }

    @Override
    public StartSimulationSessionVo start(long userId, String collectionId, String requestId,
                                          StartSimulationSessionBo command) {
        long paperId = requiredId(collectionId, "collectionId");
        normalizeRequestId(requestId);
        if (command == null) {
            throw invalid("body", "REQUIRED", "请求体不能为空");
        }
        long revisionId = requiredId(command.getExpectedRevisionId(), "expectedRevisionId");
        requireGoalVersion(command.getExpectedGoalVersion());
        FormalExamEngine.StartResult result = formalExamEngine.start(userId, paperId, "SIMULATION", "simulation",
            revisionId, command.getExpectedGoalVersion().longValueExact(), requestId);
        OffsetDateTime started = OffsetDateTime.parse(result.startedTime());
        return new StartSimulationSessionVo(OffsetDateTime.now(clock), result.sessionId(), collectionId,
            result.revisionId(), result.resumed(), result.formalAttemptNo(), result.formalAttemptNo() > 1,
            started, result.durationSeconds(), started.plusSeconds(result.durationSeconds()), result.answerPath());
    }

    @Override public FormalExamSessionVo session(long userId,long sessionId){return formalExamEngine.session(userId,sessionId,"simulation");}
    @Override public FormalExamItemVo item(long userId,long sessionId,int order){return formalExamEngine.item(userId,sessionId,order,"simulation");}
    @Override public FormalExamSessionVo saveDraft(long userId,long sessionId,int order,String requestId,FormalExamDraftBo command){return formalExamEngine.saveDraft(userId,sessionId,order,"simulation",requestId,command);}
    @Override public FormalExamTimerEventVo timerEvent(long userId,long sessionId,String requestId,FormalExamTimerEventBo command){return formalExamEngine.timerEvent(userId,sessionId,"simulation",requestId,command);}
    @Override public FormalExamSessionVo pause(long userId,long sessionId,String requestId,FormalExamPauseBo command){return formalExamEngine.pause(userId,sessionId,"simulation",requestId,command);}
    @Override public FormalExamFinishCheckVo finishCheck(long userId,long sessionId){return formalExamEngine.finishCheck(userId,sessionId,"simulation");}
    @Override public FormalExamFinishVo finish(long userId,long sessionId,String requestId,FormalExamFinishBo command){return formalExamEngine.finish(userId,sessionId,"simulation",requestId,command);}
    @Override public FormalExamStatusVo status(long userId,long sessionId){return formalExamEngine.status(userId,sessionId,"simulation");}
    @Override public FormalExamResultVo result(long userId,long sessionId){return formalExamEngine.result(userId,sessionId,"simulation");}
    @Override public FormalExamStatusVo regenerate(long userId,long sessionId,String requestId){return formalExamEngine.regenerate(userId,sessionId,"simulation",requestId);}

    private QueryValues normalize(SimulationQueryBo query) {
        SimulationQueryBo source = query == null ? new SimulationQueryBo() : query;
        Long certificationId = optionalId(source.getCertificationId(), "certificationId");
        Long syllabusVersionId = optionalId(source.getSyllabusVersionId(), "syllabusVersionId");
        String keyword = source.getKeyword();
        if (keyword != null) {
            keyword = keyword.trim();
            if (keyword.isEmpty()) {
                keyword = null;
            } else if (keyword.codePointCount(0, keyword.length()) > 100) {
                throw invalid("keyword", "OUT_OF_RANGE", "试卷名称关键字不能超过100个字符");
            }
        }
        int pageNum = source.getPageNum() == null ? 1 : source.getPageNum();
        int pageSize = source.getPageSize() == null ? 10 : source.getPageSize();
        if (pageNum < 1 || pageNum > 10000) {
            throw invalid("pageNum", "OUT_OF_RANGE", "页码必须在1到10000之间");
        }
        if (pageSize < 1 || pageSize > 50) {
            throw invalid("pageSize", "OUT_OF_RANGE", "每页数量必须在1到50之间");
        }
        return new QueryValues(certificationId, syllabusVersionId, keyword, pageNum, pageSize);
    }

    private SimulationSetupVo.CurrentGoalVo goalVo(SimulationGoalRow goal) {
        if (goal == null) {
            return null;
        }
        return new SimulationSetupVo.CurrentGoalVo(
            String.valueOf(goal.getId()), String.valueOf(goal.getCertificationId()), goal.getCertificationName(),
            String.valueOf(goal.getSyllabusVersionId()), goal.getSyllabusVersionName(), goal.getRowVersion());
    }

    private SimulationListItemVo listVo(SimulationPaperRow row, SimulationGoalRow goal) {
        return new SimulationListItemVo(
            String.valueOf(row.getCollectionId()), String.valueOf(row.getRevisionId()), row.getCollectionCode(),
            row.getCollectionName(), String.valueOf(row.getCertificationId()), row.getCertificationName(),
            String.valueOf(row.getSyllabusVersionId()), row.getSyllabusVersionName(), subject(row),
            questionTypes(row), count(row.getActualQuestionCount()), score(row.getActualTotalReportScore()),
            count(row.getDurationMinutes()), row.getPublishedTime(), access(row, goal));
    }

    private SimulationDetailVo detailVo(SimulationPaperRow row, SimulationGoalRow goal) {
        return new SimulationDetailVo(
            String.valueOf(row.getCollectionId()), String.valueOf(row.getRevisionId()), row.getCollectionName(),
            String.valueOf(row.getCertificationId()), row.getCertificationName(),
            String.valueOf(row.getSyllabusVersionId()), row.getSyllabusVersionName(), subject(row),
            questionTypes(row), count(row.getActualQuestionCount()), score(row.getActualTotalReportScore()),
            count(row.getDurationMinutes()), row.getPublishedTime(), "standard", RULES, access(row, goal));
    }

    private SimulationAccessVo access(SimulationPaperRow row, SimulationGoalRow goal) {
        boolean supported = subject(row) != null && count(row.getActualQuestionCount()) > 0
            && count(row.getDurationMinutes()) > 0 && !questionTypes(row).isEmpty()
            && questionTypes(row).stream().allMatch(type -> List.of("CHOICE", "CASE", "ESSAY").contains(type));
        if (!supported) return new SimulationAccessVo("BLOCKED", false, "SIMULATION_PAPER_UNAVAILABLE", null, null);
        if (goal == null) return new SimulationAccessVo("BLOCKED", false, "LEARNING_GOAL_NOT_ACTIVE", null, null);
        if (!Objects.equals(goal.getCertificationId(), row.getCertificationId())
            || !Objects.equals(goal.getSyllabusVersionId(), row.getSyllabusVersionId())) {
            return new SimulationAccessVo("BLOCKED", false, "SIMULATION_GOAL_SCOPE_MISMATCH", null, null);
        }
        if (row.getActiveSessionId() != null) {
            String target = "in_progress".equals(row.getActiveSessionStatus()) ? "exam" : "result";
            return new SimulationAccessVo("RESUME", true, null, String.valueOf(row.getActiveSessionId()),
                "/learning/question-bank/mock-exams/" + target + "?sessionId=" + row.getActiveSessionId());
        }
        return new SimulationAccessVo("START", true, null, null, null);
    }

    private SimulationPreviewQuestionVo previewQuestionVo(SimulationPreviewQuestionRow row) {
        return new SimulationPreviewQuestionVo(count(row.getQuestionOrder()), row.getQuestionType(), row.getStem());
    }

    private SimulationSubjectVo subject(SimulationPaperRow row) {
        if (!Integer.valueOf(1).equals(row.getSubjectCount())
            || row.getSubjectId() == null || row.getSubjectName() == null) {
            return null;
        }
        return new SimulationSubjectVo(String.valueOf(row.getSubjectId()), row.getSubjectName());
    }

    private List<String> questionTypes(SimulationPaperRow row) {
        List<String> types = new ArrayList<>(3);
        if (Boolean.TRUE.equals(row.getHasChoice())) {
            types.add("CHOICE");
        }
        if (Boolean.TRUE.equals(row.getHasCase())) {
            types.add("CASE");
        }
        if (Boolean.TRUE.equals(row.getHasEssay())) {
            types.add("ESSAY");
        }
        return List.copyOf(types);
    }

    private Long optionalId(String value, String field) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return requiredId(value, field);
    }

    private long requiredId(String value, String field) {
        if (value == null || value.isEmpty()) {
            throw invalid(field, "REQUIRED", "ID不能为空");
        }
        if (!value.equals(value.trim()) || !value.matches("[0-9]+")) {
            throw invalid(field, "INVALID_FORMAT", "必须是十进制正整数");
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw invalid(field, "OUT_OF_RANGE", "必须是十进制正整数");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalid(field, "OUT_OF_RANGE", "ID超出允许范围");
        }
    }

    private void normalizeRequestId(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            throw invalid("X-Request-Id", "REQUIRED", "请求号不能为空");
        }
        try {
            if (!requestId.equals(requestId.trim()) || requestId.length() != 36) {
                throw new IllegalArgumentException();
            }
            UUID uuid = UUID.fromString(requestId);
            if (uuid.variant() != 2 || !uuid.toString().equalsIgnoreCase(requestId)) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException exception) {
            throw invalid("X-Request-Id", "INVALID_FORMAT", "请求号必须是RFC 4122 UUID");
        }
    }

    private void requireGoalVersion(BigInteger version) {
        if (version == null) {
            throw invalid("expectedGoalVersion", "REQUIRED", "目标版本不能为空");
        }
        if (version.signum() < 0 || version.compareTo(MAX_LONG) > 0) {
            throw invalid("expectedGoalVersion", "OUT_OF_RANGE", "目标版本超出允许范围");
        }
    }

    private int count(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal score(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private SimulationException invalid(String field, String code, String message) {
        return new SimulationException(400, "SIMULATION_REQUEST_INVALID", "请求参数格式不正确", false,
            List.of(new SimulationErrorVo.FieldErrorVo(field, code, message)), null, null);
    }

    private SimulationException system(String message, RuntimeException cause) {
        return new SimulationException(500, "SIMULATION_SYSTEM_FAILURE", message, true, List.of(), null, cause);
    }

    private record QueryValues(
        Long certificationId,
        Long syllabusVersionId,
        String keyword,
        int pageNum,
        int pageSize
    ) {
    }

    private record CertificationOptions(
        long id,
        String name,
        List<SimulationSetupVo.SyllabusVersionVo> syllabuses
    ) {
        private CertificationOptions(long id, String name) {
            this(id, name, new ArrayList<>());
        }

        private SimulationSetupVo.CertificationVo toVo() {
            return new SimulationSetupVo.CertificationVo(String.valueOf(id), name, List.copyOf(syllabuses));
        }
    }
}
