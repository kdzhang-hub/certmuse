package org.dromara.certmuse.question.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import org.dromara.certmuse.shared.QuestionSemanticHasher;
import org.dromara.certmuse.question.domain.QuestionRows;
import org.dromara.certmuse.question.domain.bo.QuestionQueryBo;
import org.dromara.certmuse.question.domain.bo.QuestionSaveBo;
import org.dromara.certmuse.question.domain.vo.QuestionDetailVo;
import org.dromara.certmuse.question.domain.vo.QuestionErrorVo;
import org.dromara.certmuse.question.domain.vo.QuestionListVo;
import org.dromara.certmuse.question.domain.vo.QuestionPreviewVo;
import org.dromara.certmuse.question.domain.vo.QuestionReviewMutationVo;
import org.dromara.certmuse.question.domain.vo.QuestionSaveResultVo;
import org.dromara.certmuse.question.domain.vo.QuestionSubmitReviewResultVo;
import org.dromara.certmuse.question.domain.CollectionRevisionRow;
import org.dromara.certmuse.question.mapper.CollectionMapper;
import org.dromara.certmuse.question.mapper.QuestionMapper;
import org.dromara.certmuse.question.service.QuestionImageUrlService;
import org.dromara.certmuse.question.service.QuestionService;
import org.dromara.certmuse.question.support.QuestionException;
import org.dromara.certmuse.question.support.QuestionJsonSchema;
import org.dromara.certmuse.shared.schema.SharedJsonSchema;
import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;
import org.dromara.certmuse.shared.schema.SharedJsonSchema;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class QuestionServiceImpl implements QuestionService {
    private static final Set<String> TYPES = Set.of("CHOICE", "CASE", "ESSAY");
    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");
    private static final Set<String> STATUSES = Set.of("draft", "pending_review", "rejected", "published");
    private static final String SAVE_ACTION = "save_question_draft";
    private static final String DELETE_ACTION = "delete_question";
    private static final String SUBMIT_REVIEW_ACTION = "submit_review";
    private static final String APPROVE_REVIEW_ACTION = "approve_question_revision";
    private static final String REJECT_REVIEW_ACTION = "reject_question_revision";
    private static final String OFFLINE_REVIEW_ACTION = "offline_question_revision";

    private final QuestionMapper mapper;
    private final JsonMapper jsonMapper;
    private final QuestionImageUrlService imageUrlService;
    private final QuestionReviewSubmissionSupport reviewSubmissionSupport;
    private final CollectionMapper collectionMapper;

    @Autowired
    public QuestionServiceImpl(QuestionMapper mapper, JsonMapper jsonMapper, QuestionImageUrlService imageUrlService,
                               QuestionReviewSubmissionSupport reviewSubmissionSupport,
                               CollectionMapper collectionMapper) {
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
        this.imageUrlService = imageUrlService;
        this.reviewSubmissionSupport = reviewSubmissionSupport;
        this.collectionMapper = collectionMapper;
    }

    public PageResult<QuestionListVo> list(QuestionQueryBo query) {
        normalizeQuery(query);
        Long certificationId = nullableId(query.getCertificationId(), "certificationId");
        Long syllabusId = nullableId(query.getSyllabusVersionId(), "syllabusVersionId");
        Long subjectId = nullableId(query.getExamSubjectId(), "examSubjectId");
        Long knowledgeId = nullableId(query.getKnowledgePointId(), "knowledgePointId");
        if ((knowledgeId != null || subjectId != null) && syllabusId == null) throw invalid("科目和知识点不能脱离考纲使用");
        if (subjectId != null && mapper.countSubjectInSyllabus(syllabusId, subjectId) != 1) throw invalid("examSubjectId不属于所选考纲");
        if (knowledgeId != null) {
            List<QuestionRows.Knowledge> metadata = mapper.selectKnowledgeMetadata(List.of(knowledgeId));
            if (metadata.size() != 1 || !Objects.equals(metadata.getFirst().getSyllabusVersionId(), syllabusId)) throw invalid("knowledgePointId不属于所选考纲");
        }
        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 20 : query.getPageSize();
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) throw invalid("分页参数超出允许范围");
        long offset = (long) (pageNum - 1) * pageSize;
        Long visibleUser = visibleUserId();
        List<QuestionListVo> rows = mapper.selectQuestions(query, certificationId, syllabusId, subjectId, knowledgeId,
            visibleUser, pageSize, offset).stream().map(row -> new QuestionListVo(
                row.getQuestionId(), row.getRevisionId(), row.getRevisionNo(), row.getQuestionCode(),
                row.getSyllabusVersionId(), row.getSyllabusVersionName(), row.getStemSummary(),
                row.getExamSubjectId(), row.getExamSubjectName(), row.getQuestionType(), row.getDifficulty(),
                row.getStatus(), Boolean.TRUE.equals(row.getHasReviewOpinion()), row.getUpdatedTime())).toList();
        return PageResult.build(rows, mapper.countQuestions(query, certificationId, syllabusId, subjectId, knowledgeId, visibleUser));
    }

    public QuestionDetailVo detail(String questionId, String revisionId) {
        long qid = id(questionId, "questionId");
        Long rid = nullableId(revisionId, "revisionId");
        QuestionRows.Revision base = mapper.selectRevision(qid, rid, visibleUserId());
        if (base == null) throw notFound(rid == null ? "QUESTION_NOT_FOUND" : "QUESTION_REVISION_NOT_FOUND",
            rid == null ? "题目不存在或不可访问" : "修订不存在或不属于当前题目");
        return assembleDetail(base);
    }

    public QuestionPreviewVo preview(String questionId, String revisionId) {
        QuestionDetailVo detail = detail(questionId, revisionId);
        return new QuestionPreviewVo(detail.questionId(), detail.revisionId(), detail.stem(), detail.images(),
            detail.options(), detail.answer(), detail.analysis(), detail.reviewOpinion());
    }

    @Transactional
    public QuestionSaveResultVo save(String questionId, String requestId, QuestionSaveBo command) {
        long qid = id(questionId, "questionId");
        UUID.fromString(requestId);
        String payloadHash = hash(write(command));
        QuestionSaveResultVo replay = replaySave(requestId, payloadHash, qid);
        if (replay != null) return replay;

        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(idempotencyId, SAVE_ACTION, requestId, payloadHash,
            "question", qid, OffsetDateTime.now().plusDays(1)) == 0) {
            QuestionSaveResultVo concurrentReplay = replaySave(requestId, payloadHash, qid);
            if (concurrentReplay != null) return concurrentReplay;
        }
        long baseRevisionId = id(command.getBaseRevisionId(), "baseRevisionId");
        long rowVersion = nonNegativeLong(command.getRowVersion(), "rowVersion");
        long subjectId = id(command.getExamSubjectId(), "examSubjectId");
        QuestionRows.Revision source = mapper.lockRevision(qid, baseRevisionId, visibleUserId());
        if (source == null) throw notFound("QUESTION_REVISION_NOT_FOUND", "修订不存在或不属于当前题目");
        if (!Set.of("draft", "rejected").contains(source.getStatus())) {
            throw conflict("QUESTION_STATUS_NOT_EDITABLE", "只有草稿或已驳回状态的题目可以编辑");
        }
        if (source.getRowVersion() != rowVersion) throw new QuestionException(409, "QUESTION_VERSION_CONFLICT",
            "题目已被其他人更新，请刷新后重试", String.valueOf(source.getRevisionId()),
            String.valueOf(source.getRowVersion()), null);
        validateCommand(command, source, subjectId);
        String answerJson = answerJson(command.getAnswer());
        String contentHash = hash(command.getQuestionType() + "\n" + command.getStem().trim() + "\n" + answerJson);
        String semanticHash = QuestionSemanticHasher.hash(
            command.getQuestionType(), command.getStem(), command.getOptions().stream().map(QuestionSaveBo.OptionBo::getContent).toList()
        );
        Long userId = LoginHelper.getUserId();
        if (subjectId != source.getExamSubjectId()) {
            throw conflict("QUESTION_STATUS_NOT_EDITABLE", "考试科目已成为稳定身份，不能修改");
        }

        long targetRevisionId = source.getRevisionId();
        String targetStatus = source.getStatus();
        int updated = mapper.updateRevision(targetRevisionId, rowVersion, targetStatus, command.getQuestionType(),
            command.getDifficulty(), command.getEstimatedSeconds(), command.getStem().trim(), answerJson,
            trim(command.getAnalysis()), trim(command.getCommonMistakes()), contentHash, semanticHash, userId);
        if (updated != 1) {
            QuestionRows.Revision current = mapper.selectRevision(qid, targetRevisionId, visibleUserId());
            throw new QuestionException(409, "QUESTION_VERSION_CONFLICT", "题目已被其他人更新，请刷新后重试",
                current == null ? null : String.valueOf(current.getRevisionId()),
                current == null ? null : String.valueOf(current.getRowVersion()), null);
        }
        replaceChildren(qid, targetRevisionId, subjectId, command, userId);
        OffsetDateTime updatedTime = OffsetDateTime.now();
        QuestionSaveResultVo result = new QuestionSaveResultVo(String.valueOf(qid), String.valueOf(targetRevisionId),
            source.getRevisionNo(), String.valueOf(rowVersion + 1), targetStatus, false, updatedTime);
        mapper.completeIdempotency(idempotencyId, 200, responseEnvelope("题目修改已保存", result));
        return result;
    }

    @Transactional
    public void delete(String questionId, String requestId) {
        long qid = id(questionId, "questionId");
        UUID.fromString(requestId);
        String payloadHash = hash("delete:" + qid);
        QuestionRows.Idempotency replay = checkedReplay(DELETE_ACTION, requestId, payloadHash, qid);
        if (replay != null && "succeeded".equals(replay.getStatus())) return;
        long idemId = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(idemId, DELETE_ACTION, requestId, payloadHash,
            "question", qid, OffsetDateTime.now().plusDays(1)) == 0) {
            QuestionRows.Idempotency concurrentReplay = checkedReplay(DELETE_ACTION, requestId, payloadHash, qid);
            if (concurrentReplay != null && "succeeded".equals(concurrentReplay.getStatus())) return;
        }
        QuestionRows.Revision current = mapper.selectRevision(qid, null, visibleUserId());
        if (current == null) throw notFound("QUESTION_NOT_FOUND", "题目不存在或不可访问");
        if (!Set.of("draft", "rejected").contains(current.getStatus())) {
            throw conflict("QUESTION_DELETE_STATUS_INVALID", "仅草稿或已驳回题目可以删除");
        }
        if (mapper.countPublishedHistory(qid) > 0) {
            throw conflict("QUESTION_DELETE_PUBLISHED_HISTORY", "题目存在发布历史，不能删除");
        }
        if (mapper.countQuestionReferences(qid) > 0) {
            throw conflict("QUESTION_DELETE_REFERENCED", "题目已被题集或作答记录引用，不能删除");
        }
        if (mapper.softDeleteQuestion(qid, LoginHelper.getUserId()) != 1) throw notFound("QUESTION_NOT_FOUND", "题目不存在");
        String traceId = traceId();
        mapper.insertAudit(IdUtil.getSnowflakeNextId(), LoginHelper.getUserId(), qid,
            VersionedJsonDocumentFactory.json(jsonMapper, QuestionJsonSchema.DELETION_AUDIT_STATE, Map.of("del_flag", "0")),
            VersionedJsonDocumentFactory.json(jsonMapper, QuestionJsonSchema.DELETION_AUDIT_STATE, Map.of("del_flag", "1")), traceId);
        mapper.completeIdempotency(idemId, 200, responseEnvelope("题目已删除", null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionSubmitReviewResultVo submitReview(String revisionId, String requestId) {
        long rid = id(revisionId, "revisionId");
        String normalizedRequestId = requestId(requestId);
        String payloadHash = reviewPayloadHash(SUBMIT_REVIEW_ACTION, rid, null);
        QuestionRows.Revision revision = mapper.lockReviewRevision(rid, visibleUserId());
        if (revision == null) throw notFound("QUESTION_REVISION_NOT_FOUND", "修订不存在或不可访问");
        QuestionSubmitReviewResultVo replay = replaySubmitReview(normalizedRequestId, payloadHash, rid);
        if (replay != null) return replay;
        if (!Set.of("draft", "rejected").contains(revision.getStatus())) {
            throw new QuestionException(409, "QUESTION_REVIEW_INVALID_STATUS", "当前题目修订不是草稿或已驳回，不能提交审核");
        }

        List<QuestionErrorVo.BlockingIssueVo> issues = reviewSubmissionSupport.validate(revision);
        if (!issues.isEmpty()) {
            throw new QuestionException(422, "QUESTION_SUBMIT_REVIEW_CHECK_FAILED", "提交审核前的发布门禁未通过", issues);
        }

        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(idempotencyId, SUBMIT_REVIEW_ACTION, normalizedRequestId, payloadHash,
            "question_revision", rid, OffsetDateTime.now().plusDays(1)) == 0) {
            QuestionSubmitReviewResultVo concurrentReplay = replaySubmitReview(normalizedRequestId, payloadHash, rid);
            if (concurrentReplay != null) return concurrentReplay;
            throw reviewConflict();
        }
        Long userId = LoginHelper.getUserId();
        QuestionSubmitReviewResultVo result = submitReviewResult(revision);
        String traceId = traceId();
        reviewSubmissionSupport.submitDraft(revision, userId, normalizedRequestId, traceId);
        mapper.completeIdempotency(idempotencyId, 200, responseEnvelope("题目已提交审核", result));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionReviewMutationVo approve(String revisionId, String requestId) {
        long rid = id(revisionId, "revisionId");
        String normalizedRequestId = requestId(requestId);
        String payloadHash = reviewPayloadHash(APPROVE_REVIEW_ACTION, rid, null);
        QuestionReviewMutationVo replay = replayReview(normalizedRequestId, payloadHash);
        if (replay != null) return replay;

        QuestionRows.Revision revision = mapper.lockReviewRevision(rid, visibleUserId());
        if (revision == null) throw notFound("QUESTION_REVISION_NOT_FOUND", "修订不存在或不可访问");
        QuestionReviewMutationVo lockedReplay = replayReview(normalizedRequestId, payloadHash);
        if (lockedReplay != null) return lockedReplay;
        requirePendingReview(revision);
        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertReviewIdempotency(idempotencyId, APPROVE_REVIEW_ACTION, normalizedRequestId,
            payloadHash, revision.getQuestionId(), OffsetDateTime.now().plusDays(1)) == 0) {
            QuestionReviewMutationVo concurrentReplay = replayReview(normalizedRequestId, payloadHash);
            if (concurrentReplay != null) return concurrentReplay;
        }

        Long userId = LoginHelper.getUserId();
        if (mapper.markReviewPublished(rid, userId) != 1) throw reviewConflict();

        QuestionReviewMutationVo result = reviewResult(revision, "published");
        String traceId = traceId();
        mapper.insertRevisionEvent(IdUtil.getSnowflakeNextId(), revision.getQuestionId(), rid, "review_approved",
            "pending_review", "published", null, userId, normalizedRequestId, traceId);
        insertReviewAudit(revision, "review_approve", "pending_review", "published", null, userId,
            normalizedRequestId, traceId);
        mapper.completeIdempotency(idempotencyId, 200, responseEnvelope("题目已审核并发布", result));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionReviewMutationVo reject(String revisionId, String requestId, String reviewOpinion) {
        long rid = id(revisionId, "revisionId");
        String normalizedRequestId = requestId(requestId);
        String opinion = trim(reviewOpinion);
        if (opinion == null || opinion.length() > 500) throw reviewOpinionRequired();
        String payloadHash = reviewPayloadHash(REJECT_REVIEW_ACTION, rid, opinion);
        QuestionReviewMutationVo replay = replayReview(normalizedRequestId, payloadHash);
        if (replay != null) return replay;

        QuestionRows.Revision revision = mapper.lockReviewRevision(rid, visibleUserId());
        if (revision == null) throw notFound("QUESTION_REVISION_NOT_FOUND", "修订不存在或不可访问");
        QuestionReviewMutationVo lockedReplay = replayReview(normalizedRequestId, payloadHash);
        if (lockedReplay != null) return lockedReplay;
        requirePendingReview(revision);
        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertReviewIdempotency(idempotencyId, REJECT_REVIEW_ACTION, normalizedRequestId,
            payloadHash, revision.getQuestionId(), OffsetDateTime.now().plusDays(1)) == 0) {
            QuestionReviewMutationVo concurrentReplay = replayReview(normalizedRequestId, payloadHash);
            if (concurrentReplay != null) return concurrentReplay;
        }

        Long userId = LoginHelper.getUserId();
        if (mapper.markReviewRejected(rid, userId, opinion) != 1) throw reviewConflict();

        QuestionReviewMutationVo result = reviewResult(revision, "rejected");
        String traceId = traceId();
        mapper.insertRevisionEvent(IdUtil.getSnowflakeNextId(), revision.getQuestionId(), rid, "review_rejected",
            "pending_review", "rejected", opinion, userId, normalizedRequestId, traceId);
        insertReviewAudit(revision, "review_reject", "pending_review", "rejected", opinion, userId,
            normalizedRequestId, traceId);
        cascadeRejectedQuestion(revision, opinion, userId, normalizedRequestId, traceId);
        mapper.completeIdempotency(idempotencyId, 200, responseEnvelope("题目已驳回，等待修改", result));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionReviewMutationVo takeOffline(String revisionId, String requestId) {
        long rid = id(revisionId, "revisionId");
        String normalizedRequestId = requestId(requestId);
        String payloadHash = reviewPayloadHash(OFFLINE_REVIEW_ACTION, rid, null);
        QuestionReviewMutationVo replay = replayReview(normalizedRequestId, payloadHash);
        if (replay != null) return replay;

        QuestionRows.Revision revision = mapper.lockReviewRevision(rid, visibleUserId());
        if (revision == null) throw notFound("QUESTION_REVISION_NOT_FOUND", "修订不存在或不可访问");
        QuestionReviewMutationVo lockedReplay = replayReview(normalizedRequestId, payloadHash);
        if (lockedReplay != null) return lockedReplay;
        if (!"published".equals(revision.getStatus())) {
            throw new QuestionException(409, "QUESTION_OFFLINE_INVALID_STATUS", "仅已发布题目可以下架");
        }
        long idempotencyId = IdUtil.getSnowflakeNextId();
        if (mapper.insertReviewIdempotency(idempotencyId, OFFLINE_REVIEW_ACTION, normalizedRequestId,
            payloadHash, revision.getQuestionId(), OffsetDateTime.now().plusDays(1)) == 0) {
            QuestionReviewMutationVo concurrentReplay = replayReview(normalizedRequestId, payloadHash);
            if (concurrentReplay != null) return concurrentReplay;
        }

        Long userId = LoginHelper.getUserId();
        if (mapper.markPublishedDraft(rid, userId) != 1) throw reviewConflict();
        QuestionReviewMutationVo result = reviewResult(revision, "draft");
        String traceId = traceId();
        mapper.insertRevisionEvent(IdUtil.getSnowflakeNextId(), revision.getQuestionId(), rid, "question_offline",
            "published", "draft", null, userId, normalizedRequestId, traceId);
        insertReviewAudit(revision, "question_offline", "published", "draft", null, userId,
            normalizedRequestId, traceId);
        mapper.completeIdempotency(idempotencyId, 200, responseEnvelope("题目已下架并退回草稿", result));
        return result;
    }

    private QuestionDetailVo assembleDetail(QuestionRows.Revision base) {
        List<QuestionRows.Knowledge> knowledge = mapper.selectKnowledge(base.getRevisionId());
        Set<Long> syllabuses = knowledge.stream().map(QuestionRows.Knowledge::getSyllabusVersionId).collect(Collectors.toSet());
        if (syllabuses.size() > 1) throw conflict("QUESTION_SYLLABUS_IMMUTABLE", "修订关联了多个考纲，请先修复数据");
        Long syllabusId = syllabuses.isEmpty() ? null : syllabuses.iterator().next();
        QuestionRows.IdLabel syllabus = syllabusId == null ? null : mapper.selectSyllabusLabel(syllabusId);
        return new QuestionDetailVo(String.valueOf(base.getQuestionId()), String.valueOf(base.getRevisionId()),
            base.getRevisionNo(), String.valueOf(base.getRowVersion()), base.getQuestionCode(),
            syllabusId == null ? null : String.valueOf(syllabusId),
            syllabus == null ? null : syllabus.getLabel(), String.valueOf(base.getCertificationId()), base.getCertificationName(),
            String.valueOf(base.getExamSubjectId()), base.getExamSubjectName(), false,
            mapper.selectExamSubjectOptions(base.getCertificationId()).stream().map(row -> new QuestionDetailVo.IdLabelVo(row.getId(), row.getLabel())).toList(),
            base.getQuestionType(), base.getDifficulty(), base.getEstimatedSeconds(), base.getStem(),
            images(base.getRevisionId()), mapper.selectOptions(base.getRevisionId()).stream().map(row -> new QuestionDetailVo.OptionVo(row.getLabel(), row.getContent(), row.getSortOrder())).toList(),
            parseJson(base.getAnswer()), base.getAnalysis(), base.getCommonMistakes(),
            knowledge.stream().map(row -> new QuestionDetailVo.KnowledgeBindingVo(String.valueOf(row.getKnowledgePointId()), row.getKnowledgePointLabel(), row.getRelationRole(), row.getSortOrder())).toList(),
            base.getStatus(), base.getReviewOpinion(), editableMode(base.getStatus()), base.getUpdatedTime());
    }

    private List<QuestionDetailVo.ImageVo> images(long revisionId) {
        return mapper.selectImages(revisionId).stream().map(row -> new QuestionDetailVo.ImageVo(String.valueOf(row.getId()),
            row.getSortOrder(), imageUrlService.accessUrl(row.getSourceUrl(), row.getStoragePath()), row.getAlt())).toList();
    }

    private void validateCommand(QuestionSaveBo command, QuestionRows.Revision source, long subjectId) {
        if (!SharedJsonSchema.QUESTION_ANSWER.version().equals(command.getAnswer().getSchemaVersion())) throw answerInvalid("答案schemaVersion必须为1.0");
        if (!TYPES.contains(command.getQuestionType())) throw typeInvalid("不支持的题型");
        if (command.getDifficulty() != null && !DIFFICULTIES.contains(command.getDifficulty())) throw typeInvalid("难度非法");
        if (command.getEstimatedSeconds() != null && command.getEstimatedSeconds() <= 0) throw typeInvalid("预计时间必须大于0");
        command.setStem(command.getStem().trim());
        if (command.getStem().isEmpty()) throw typeInvalid("题干不能为空");
        validateBindings(command, source, subjectId);
        if ("CHOICE".equals(command.getQuestionType())) validateChoice(command); else validateSubjective(command);
    }

    private void validateBindings(QuestionSaveBo command, QuestionRows.Revision source, long subjectId) {
        List<QuestionRows.Knowledge> existing = mapper.selectKnowledge(source.getRevisionId());
        if (command.getKnowledgeBindings().isEmpty()) {
            if (!existing.isEmpty()) throw primaryInvalid("已有知识点的题目不能清空全部知识点绑定");
            return;
        }
        if (command.getKnowledgeBindings().stream().filter(b -> "primary".equals(b.getRelationRole())).count() != 1) throw primaryInvalid("必须且只能有一个主知识点");
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < command.getKnowledgeBindings().size(); i++) {
            QuestionSaveBo.KnowledgeBindingBo item = command.getKnowledgeBindings().get(i);
            if (!Set.of("primary", "secondary").contains(item.getRelationRole()) || item.getSortOrder() != i) throw primaryInvalid("知识点角色或顺序非法");
            if (!ids.add(id(item.getKnowledgePointId(), "knowledgePointId"))) throw primaryInvalid("知识点不能重复");
        }
        Long sourceSyllabus = existing.isEmpty() ? null : uniqueSyllabus(existing);
        validateKnowledgeMetadata(ids, sourceSyllabus, subjectId);
    }

    private void validateChoice(QuestionSaveBo command) {
        if (command.getOptions().size() < 2 || command.getOptions().size() > 10) throw typeInvalid("选择题选项数量必须为2至10");
        Set<String> labels = new HashSet<>();
        for (int i = 0; i < command.getOptions().size(); i++) {
            QuestionSaveBo.OptionBo option = command.getOptions().get(i);
            if (!labels.add(option.getLabel()) || option.getSortOrder() != i + 1) throw typeInvalid("选项标签或顺序非法");
        }
        QuestionSaveBo.AnswerBo answer = command.getAnswer();
        if (!"option_keys".equals(answer.getAnswerType()) || !"single".equals(answer.getSelectionMode()) || !(answer.getValue() instanceof List<?> values) || values.size() > 1) {
            throw answerInvalid("当前选择题只支持单选答案");
        }
        if (!values.isEmpty() && (! (values.getFirst() instanceof String key) || !labels.contains(key))) throw answerInvalid("答案未命中当前选项");
    }

    private void validateSubjective(QuestionSaveBo command) {
        if (!command.getOptions().isEmpty()) throw typeInvalid("案例题和论文题不能包含选项");
        if (!"reference_text".equals(command.getAnswer().getAnswerType())
            || !(command.getAnswer().getValue() instanceof String value) || value.isBlank()) {
            throw answerInvalid("案例题和论文题必须提供非空参考答案");
        }
    }

    private void validateKnowledgeMetadata(Collection<Long> ids, Long syllabusId, long subjectId) {
        List<QuestionRows.Knowledge> rows = mapper.selectKnowledgeMetadata(ids);
        if (rows.size() != ids.size()) throw new QuestionException(400, "QUESTION_KNOWLEDGE_NOT_FOUND", "知识点不存在");
        for (QuestionRows.Knowledge row : rows) {
            if (!Boolean.TRUE.equals(row.getLeaf())) throw new QuestionException(400, "QUESTION_KNOWLEDGE_NOT_LEAF", "只能绑定最末级知识点");
            if (syllabusId != null && !Objects.equals(row.getSyllabusVersionId(), syllabusId)) throw conflict("QUESTION_SYLLABUS_IMMUTABLE", "知识点不属于题目既有考纲");
            if (!Objects.equals(row.getExamSubjectId(), subjectId)) throw primaryInvalid("知识点不属于当前考试科目");
        }
    }

    private void replaceChildren(long questionId, long revisionId, long subjectId, QuestionSaveBo command, Long userId) {
        mapper.deleteOptions(revisionId); mapper.deleteQuestionKnowledge(revisionId);
        for (QuestionSaveBo.OptionBo option : command.getOptions()) mapper.insertOption(IdUtil.getSnowflakeNextId(), revisionId, option.getLabel(), option.getContent().trim(), option.getSortOrder(), userId);
        for (QuestionSaveBo.KnowledgeBindingBo binding : command.getKnowledgeBindings()) mapper.insertKnowledge(IdUtil.getSnowflakeNextId(), revisionId, questionId,
            id(binding.getKnowledgePointId(), "knowledgePointId"), subjectId, binding.getRelationRole(), binding.getSortOrder());
    }

    private long uniqueSyllabus(List<QuestionRows.Knowledge> knowledge) {
        Set<Long> ids = knowledge.stream().map(QuestionRows.Knowledge::getSyllabusVersionId).collect(Collectors.toSet());
        if (ids.size() != 1) throw conflict("QUESTION_SYLLABUS_IMMUTABLE", "修订无法确定唯一所属考纲");
        return ids.iterator().next();
    }

    private QuestionSaveResultVo replaySave(String requestId, String payloadHash, long questionId) {
        QuestionRows.Idempotency record = checkedReplay(SAVE_ACTION, requestId, payloadHash, questionId);
        if (record == null || !"succeeded".equals(record.getStatus())) return null;
        try {
            JsonNode root = jsonMapper.readTree(record.getResponseBody());
            return jsonMapper.treeToValue(root.path("response").path("data"), QuestionSaveResultVo.class);
        } catch (Exception exception) {
            throw new QuestionException(500, "QUESTION_OPERATION_FAILURE", "读取幂等结果失败", exception);
        }
    }

    private QuestionSubmitReviewResultVo replaySubmitReview(String requestId, String payloadHash, long revisionId) {
        QuestionRows.Idempotency record = checkedReplay(SUBMIT_REVIEW_ACTION, requestId, payloadHash, revisionId);
        if (record == null || !"succeeded".equals(record.getStatus())) return null;
        try {
            JsonNode root = jsonMapper.readTree(record.getResponseBody());
            return jsonMapper.treeToValue(root.path("response").path("data"), QuestionSubmitReviewResultVo.class);
        } catch (Exception exception) {
            throw new QuestionException(500, "QUESTION_OPERATION_FAILURE", "读取幂等结果失败", exception);
        }
    }

    private QuestionReviewMutationVo replayReview(String requestId, String payloadHash) {
        QuestionRows.Idempotency record = mapper.selectReviewIdempotency(requestId);
        if (record == null) return null;
        if (!Objects.equals(record.getPayloadHash(), payloadHash)) {
            throw new QuestionException(409, "QUESTION_REVIEW_REQUEST_CONFLICT", "X-Request-Id已被其他审核请求使用");
        }
        if (!"succeeded".equals(record.getStatus())) throw reviewConflict();
        try {
            JsonNode root = jsonMapper.readTree(record.getResponseBody());
            return jsonMapper.treeToValue(root.path("response").path("data"), QuestionReviewMutationVo.class);
        } catch (Exception exception) {
            throw new QuestionException(500, "QUESTION_OPERATION_FAILURE", "读取幂等结果失败", exception);
        }
    }

    private void insertReviewAudit(QuestionRows.Revision revision, String action, String fromStatus,
                                   String toStatus, String opinion, Long userId, String requestId, String traceId) {
        Map<String, Object> before = reviewAuditData(revision, fromStatus, null, null, requestId);
        Map<String, Object> after = reviewAuditData(revision, toStatus, userId, opinion, requestId);
        mapper.insertReviewAudit(IdUtil.getSnowflakeNextId(), userId, action, revision.getRevisionId(),
            write(before), write(after), traceId);
    }

    private static Map<String, Object> reviewAuditData(QuestionRows.Revision revision, String status,
                                                        Long reviewerId, String opinion, String requestId) {
        Map<String, Object> data = VersionedJsonDocumentFactory.flatFields(
            QuestionJsonSchema.QUESTION_REVIEW_AUDIT, Map.of());
        data.put("content_type", "question_revision");
        data.put("aggregate_id", String.valueOf(revision.getQuestionId()));
        data.put("revision_id", String.valueOf(revision.getRevisionId()));
        data.put("status", status);
        data.put("request_id", requestId);
        if (reviewerId != null) data.put("reviewer_id", String.valueOf(reviewerId));
        if (opinion != null) data.put("review_opinion", opinion);
        return data;
    }

    private void cascadeRejectedQuestion(QuestionRows.Revision question, String opinion, Long userId,
                                         String requestId, String traceId) {
        String cascadeOpinion = "题目" + question.getQuestionCode() + "（修订" + question.getRevisionId()
            + "）审核被驳回：" + opinion;
        for (CollectionRevisionRow collection : collectionMapper.lockPendingRevisionsByQuestionRevision(
            question.getRevisionId())) {
            if (collectionMapper.markRejected(collection.getId(), userId, cascadeOpinion) != 1) {
                throw reviewConflict();
            }
            collectionMapper.insertReviewAudit(IdUtil.getSnowflakeNextId(), userId,
                "reject_collection_by_question", collection.getId(),
                write(collectionCascadeAuditData(collection, "pending_review", null, question, requestId)),
                write(collectionCascadeAuditData(collection, "rejected", cascadeOpinion, question, requestId)),
                traceId);
        }
    }

    private static Map<String, Object> collectionCascadeAuditData(CollectionRevisionRow collection, String status,
                                                                   String opinion, QuestionRows.Revision question,
                                                                   String requestId) {
        Map<String, Object> data = VersionedJsonDocumentFactory.flatFields(
            QuestionJsonSchema.COLLECTION_REVIEW_AUDIT, Map.of());
        data.put("collection_id", String.valueOf(collection.getCollectionId()));
        data.put("revision_id", String.valueOf(collection.getId()));
        data.put("status", status);
        data.put("row_version", String.valueOf(collection.getRowVersion() + ("rejected".equals(status) ? 1 : 0)));
        data.put("trigger_question_code", question.getQuestionCode());
        data.put("trigger_question_revision_id", String.valueOf(question.getRevisionId()));
        data.put("request_id", requestId);
        if (opinion != null) data.put("review_opinion", opinion);
        return data;
    }

    private static QuestionReviewMutationVo reviewResult(QuestionRows.Revision revision, String status) {
        return new QuestionReviewMutationVo(String.valueOf(revision.getQuestionId()),
            String.valueOf(revision.getRevisionId()), status, String.valueOf(revision.getRowVersion() + 1));
    }

    private static QuestionSubmitReviewResultVo submitReviewResult(QuestionRows.Revision revision) {
        return new QuestionSubmitReviewResultVo(String.valueOf(revision.getQuestionId()),
            String.valueOf(revision.getRevisionId()), "pending_review", String.valueOf(revision.getRowVersion() + 1));
    }

    private static void requirePendingReview(QuestionRows.Revision revision) {
        if (!"pending_review".equals(revision.getStatus())) {
            throw new QuestionException(409, "QUESTION_REVIEW_INVALID_STATUS", "当前题目修订不在待审核状态");
        }
    }

    private static QuestionException reviewConflict() {
        return new QuestionException(409, "QUESTION_REVISION_CONFLICT", "题目修订已被其他操作更新，请重试");
    }

    private static QuestionException reviewOpinionRequired() {
        return new QuestionException(400, "REVIEW_OPINION_REQUIRED", "审核意见不能为空且不能超过500字符");
    }

    private QuestionRows.Idempotency checkedReplay(String action, String requestId, String payloadHash, long questionId) {
        QuestionRows.Idempotency record = mapper.selectIdempotency(action, requestId);
        if (record == null) return null;
        if (!Objects.equals(record.getPayloadHash(), payloadHash) || !Objects.equals(record.getResourceId(), questionId)) throw invalid("X-Request-Id已被其他请求使用");
        if (!"succeeded".equals(record.getStatus())) throw conflict("QUESTION_STATUS_NOT_EDITABLE", "相同请求正在处理中，请稍后重试");
        return record;
    }

    private String answerJson(QuestionSaveBo.AnswerBo answer) {
        Map<String, Object> value = VersionedJsonDocumentFactory.flatFields(SharedJsonSchema.QUESTION_ANSWER, Map.of());
        value.put("answer_type", answer.getAnswerType());
        if (answer.getSelectionMode() != null) value.put("selection_mode", answer.getSelectionMode()); value.put("value", answer.getValue());
        return write(value);
    }
    private Object parseJson(String value) { try { if (value == null) return null; JsonNode node=jsonMapper.readTree(value); Map<String,Object> answer=new LinkedHashMap<>(); answer.put("schemaVersion",node.path("schema_version").asText()); answer.put("answerType",node.path("answer_type").asText()); if(node.has("selection_mode")) answer.put("selectionMode",node.path("selection_mode").asText()); answer.put("value",jsonMapper.treeToValue(node.path("value"),Object.class)); return answer; } catch (Exception e) { throw new QuestionException(500,"QUESTION_OPERATION_FAILURE","题目答案数据损坏",e); } }
    private String responseEnvelope(String message, Object data) { return VersionedJsonDocumentFactory.json(jsonMapper, QuestionJsonSchema.OPERATION_RESPONSE, Map.of("response",new LinkedHashMap<>(Map.of("code",200,"msg",message,"data",data == null ? Map.of() : data)))); }
    private String write(Object value) { try { return jsonMapper.writeValueAsString(value); } catch (Exception e) { throw new QuestionException(500,"QUESTION_OPERATION_FAILURE","序列化题目数据失败",e); } }
    private static String hash(String value) { return DigestUtil.sha256Hex(value); }
    private static String reviewPayloadHash(String action, long revisionId, String opinion) {
        return hash(action + "\n" + revisionId + "\n" + (opinion == null ? "" : opinion));
    }
    private static String traceId() { return UUID.randomUUID().toString(); }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String requestId(String value) {
        try { return UUID.fromString(value).toString(); }
        catch (IllegalArgumentException exception) { throw invalid("X-Request-Id必须是UUID"); }
    }
    private static String editableMode(String status) { return Set.of("draft", "rejected").contains(status) ? "update_working_revision" : "readonly"; }
    private static Long visibleUserId() { return LoginHelper.isSuperAdmin() ? null : LoginHelper.getUserId(); }

    private static void normalizeQuery(QuestionQueryBo query) {
        query.setKeyword(trim(query.getKeyword())); if (query.getKeyword()!=null && query.getKeyword().length()>200) throw invalid("keyword长度不能超过200字符");
        query.setCertificationId(trim(query.getCertificationId()));
        query.setQuestionType(trim(query.getQuestionType())); query.setDifficulty(trim(query.getDifficulty())); query.setStatus(trim(query.getStatus()));
        if (query.getQuestionType()!=null && !TYPES.contains(query.getQuestionType())) throw invalid("questionType非法");
        if (query.getDifficulty()!=null && !DIFFICULTIES.contains(query.getDifficulty())) throw invalid("difficulty非法");
        if (query.getStatus()!=null && !STATUSES.contains(query.getStatus())) throw invalid("status非法");
    }
    private static long id(String value,String field) { try { if(value==null||!value.matches("^[1-9][0-9]{0,18}$")) throw new NumberFormatException(); return Long.parseLong(value); } catch(NumberFormatException e){ throw invalid(field+"必须是十进制正整数"); } }
    private static Long nullableId(String value,String field) { return value==null||value.isBlank()?null:id(value,field); }
    private static long nonNegativeLong(String value,String field) { try { long parsed=Long.parseLong(value); if(parsed<0) throw new NumberFormatException(); return parsed; } catch(NumberFormatException e){ throw invalid(field+"必须是非负整数"); } }
    private static QuestionException invalid(String message) { return new QuestionException(400,"QUESTION_QUERY_INVALID",message); }
    private static QuestionException typeInvalid(String message) { return new QuestionException(400,"QUESTION_TYPE_FIELDS_INVALID",message); }
    private static QuestionException answerInvalid(String message) { return new QuestionException(400,"QUESTION_ANSWER_INVALID",message); }
    private static QuestionException primaryInvalid(String message) { return new QuestionException(400,"QUESTION_PRIMARY_KNOWLEDGE_INVALID",message); }
    private static QuestionException conflict(String code,String message) { return new QuestionException(409,code,message); }
    private static QuestionException notFound(String code,String message) { return new QuestionException(404,code,message); }
}
