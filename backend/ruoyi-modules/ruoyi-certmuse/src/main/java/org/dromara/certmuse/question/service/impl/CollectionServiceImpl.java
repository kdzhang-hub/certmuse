package org.dromara.certmuse.question.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.question.domain.CollectionIdempotencyRow;
import org.dromara.certmuse.question.domain.CollectionItemRow;
import org.dromara.certmuse.question.domain.CollectionListRow;
import org.dromara.certmuse.question.domain.CollectionRevisionRow;
import org.dromara.certmuse.question.domain.QuestionRows;
import org.dromara.certmuse.question.domain.bo.CollectionQueryBo;
import org.dromara.certmuse.question.domain.bo.CollectionRenameBo;
import org.dromara.certmuse.question.domain.bo.CollectionRevisionCreateBo;
import org.dromara.certmuse.question.domain.bo.CollectionSaveBo;
import org.dromara.certmuse.question.domain.vo.CollectionDetailVo;
import org.dromara.certmuse.question.domain.vo.CollectionListVo;
import org.dromara.certmuse.question.domain.vo.CollectionManageVo;
import org.dromara.certmuse.question.domain.vo.CollectionMutationVo;
import org.dromara.certmuse.question.domain.vo.CollectionPublishCheckVo;
import org.dromara.certmuse.question.domain.vo.CollectionRenameVo;
import org.dromara.certmuse.question.domain.vo.CollectionRevisionDetailVo;
import org.dromara.certmuse.question.domain.vo.QuestionErrorVo;
import org.dromara.certmuse.question.mapper.CollectionMapper;
import org.dromara.certmuse.question.service.CollectionService;
import org.dromara.certmuse.question.support.CollectionException;
import org.dromara.certmuse.question.support.QuestionJsonSchema;
import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CollectionServiceImpl implements CollectionService {
    private static final Set<String> TYPES = Set.of("FIRST_DIAGNOSTIC", "PRACTICE", "SIMULATION", "PAST_PAPER");
    private static final Set<String> LIST_STATUSES = Set.of("draft", "pending_review", "rejected", "published");
    private static final Set<String> SUBJECTIVE_TYPES = Set.of("CASE", "ESSAY");

    @Override
    public boolean isFirstDiagnosticReady(long revisionId) {
        CollectionRevisionRow revision = mapper.selectRevision(revisionId);
        return revision != null && "published".equals(revision.getStatus())
            && "FIRST_DIAGNOSTIC".equals(revision.getCollectionType())
            && publishCheck(revision).blockingIssues().isEmpty();
    }

    private final CollectionMapper mapper;
    private final JsonMapper jsonMapper;
    private final QuestionReviewSubmissionSupport reviewSubmissionSupport;

    @Override
    public PageResult<CollectionListVo> list(CollectionQueryBo query) {
        normalizeQuery(query);
        Long certificationId = nullableId(query.getCertificationId(), "certificationId");
        Long syllabusVersionId = nullableId(query.getSyllabusVersionId(), "syllabusVersionId");
        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 20 : query.getPageSize();
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) throw invalid("分页参数超出允许范围");
        long offset = (long) (pageNum - 1) * pageSize;
        List<CollectionListVo> rows = mapper.selectCollections(query, certificationId, syllabusVersionId,
            pageSize, offset).stream().map(this::toListVo).toList();
        return PageResult.build(rows, mapper.countCollections(query, certificationId, syllabusVersionId));
    }

    @Override
    public PageResult<CollectionManageVo> manageList(CollectionQueryBo query) {
        normalizeManageQuery(query);
        Long certificationId = nullableId(query.getCertificationId(), "certificationId");
        Long syllabusVersionId = nullableId(query.getSyllabusVersionId(), "syllabusVersionId");
        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 20 : query.getPageSize();
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) throw invalid("分页参数超出允许范围");
        List<Long> collectionIds = mapper.selectManagedCollectionIds(query, certificationId, syllabusVersionId,
            pageSize, (long) (pageNum - 1) * pageSize);
        if (collectionIds.isEmpty()) return PageResult.build(List.of(), 0);
        Map<Long, CollectionManageBuilder> grouped = new LinkedHashMap<>();
        for (Long collectionId : collectionIds) grouped.put(collectionId, null);
        for (CollectionListRow row : mapper.selectManagedCollectionRevisions(collectionIds)) {
            long collectionId = Long.parseLong(row.getCollectionId());
            CollectionManageBuilder builder = grouped.get(collectionId);
            if (builder == null) {
                builder = new CollectionManageBuilder(row);
                grouped.put(collectionId, builder);
            }
            builder.add(row);
        }
        return PageResult.build(grouped.values().stream().filter(Objects::nonNull)
            .map(CollectionManageBuilder::build).toList(),
            mapper.countManagedCollections(query, certificationId, syllabusVersionId));
    }

    @Override
    public CollectionDetailVo detail(String collectionId) {
        long cid = id(collectionId, "collectionId");
        CollectionRevisionRow display = mapper.selectDisplayRevision(cid);
        if (display == null) throw notFound("COLLECTION_NOT_FOUND", "题集不存在");
        CollectionRevisionRow published = mapper.selectCurrentPublished(cid);
        CollectionRevisionRow pendingReview = mapper.selectPendingReviewRevision(cid);
        List<CollectionDetailVo.RevisionSummaryVo> histories = mapper.selectRevisions(cid).stream()
            .map(row -> summary(row, published)).toList();
        return new CollectionDetailVo(String.valueOf(cid), display.getCollectionCode(), detailVo(display, pendingReview != null),
            published == null ? null : summary(published, published), histories,
            actions(display.getStatus(), pendingReview != null));
    }

    @Override
    public CollectionRevisionDetailVo revisionDetail(String revisionId) {
        CollectionRevisionRow revision = requireRevision(id(revisionId, "revisionId"));
        return detailVo(revision, mapper.selectPendingReviewRevision(revision.getCollectionId()) != null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectionMutationVo create(String requestId, CollectionSaveBo command) {
        UUID.fromString(requestId);
        validateBasic(command, false);
        String hash = hash(write(command));
        CollectionMutationVo replay = replay("create_collection", requestId, hash, null);
        if (replay != null) return replay;
        long idemId = startIdempotency("create_collection", requestId, hash, null);
        long collectionId = IdUtil.getSnowflakeNextId();
        long revisionId = IdUtil.getSnowflakeNextId();
        long certificationId = id(command.getCertificationId(), "certificationId");
        long syllabusId = id(command.getSyllabusVersionId(), "syllabusVersionId");
        ValidatedItems validated = validateItems(command, certificationId, syllabusId);
        String code = collectionCode(collectionId);
        if (mapper.insertCollection(collectionId, certificationId, syllabusId, code,
            command.getCollectionName().trim(), command.getCollectionType(), command.getExamYear(), command.getExamMonth(),
            command.getPaperTypeCode(), command.getPaperTypeName()) != 1) {
            throw conflict("COLLECTION_CREATE_CONFLICT", "题集创建失败");
        }
        mapper.insertRevision(revisionId, collectionId, 1, command.getCollectionName().trim(),
            command.getCollectionType(), certificationId, syllabusId, command.getDurationMinutes(),
            command.getItems().size(), validated.totalScore());
        insertItems(revisionId, command, validated.metadata());
        CollectionMutationVo result = mutation(collectionId, code, revisionId, 1, "draft", 0, false);
        finishIdempotency(idemId, collectionId, result, "题集草稿已创建");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectionMutationVo save(String revisionId, String requestId, CollectionSaveBo command) {
        long rid = id(revisionId, "revisionId");
        UUID.fromString(requestId);
        validateBasic(command, true);
        String hash = hash(rid + "\n" + write(command));
        CollectionMutationVo replay = replay("save_collection_revision", requestId, hash, rid);
        if (replay != null) return replay;
        long idemId = startIdempotency("save_collection_revision", requestId, hash, rid);
        CollectionRevisionRow revision = mapper.lockRevision(rid);
        if (revision == null) throw notFound("COLLECTION_REVISION_NOT_FOUND", "题集修订不存在");
        requireEditableStatus(revision);
        long rowVersion = nonNegative(command.getRowVersion(), "rowVersion");
        if (revision.getRowVersion() != rowVersion) throw versionConflict(revision);
        long certificationId = id(command.getCertificationId(), "certificationId");
        long syllabusId = id(command.getSyllabusVersionId(), "syllabusVersionId");
        ValidatedItems validated = validateItems(command, certificationId, syllabusId);
        if (mapper.updateDraft(rid, rowVersion, command.getCollectionName().trim(), command.getCollectionType(),
            certificationId, syllabusId, command.getDurationMinutes(), command.getItems().size(), validated.totalScore()) != 1) {
        CollectionRevisionRow current = mapper.selectRevision(rid);
            if (current == null) throw notFound("COLLECTION_REVISION_NOT_FOUND", "题集修订不存在");
            if (!Set.of("draft", "rejected").contains(current.getStatus())) throw statusConflict();
            throw versionConflict(current);
        }
        mapper.updatePastPaperMetadata(revision.getCollectionId(), command.getCollectionType(), command.getExamYear(),
            command.getExamMonth(), command.getPaperTypeCode(), command.getPaperTypeName());
        mapper.deleteItems(rid);
        insertItems(rid, command, validated.metadata());
        CollectionMutationVo result = mutation(revision.getCollectionId(), revision.getCollectionCode(), rid,
            revision.getRevisionNo(), revision.getStatus(), rowVersion + 1, false);
        finishIdempotency(idemId, rid, result, "题集草稿已保存");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectionRenameVo rename(String collectionId, String requestId, CollectionRenameBo command) {
        long cid = id(collectionId, "collectionId");
        UUID.fromString(requestId);
        String name = trim(command.getCollectionName());
        if (name == null || name.length() > 200) throw invalid("题集名称不能为空且不能超过200字符");
        String hash = hash("rename-collection:" + cid + "\n" + name);
        CollectionRenameVo replay = replayRename("rename_collection", requestId, hash, cid);
        if (replay != null) return replay;
        long idemId = startRenameIdempotency("rename_collection", requestId, hash, cid);
        if (mapper.lockCollection(cid) == null) throw notFound("COLLECTION_NOT_FOUND", "题集不存在");
        if (mapper.lockDraftRevisionForRename(cid) == null) {
            throw conflict("COLLECTION_RENAME_INVALID_STATE", "只有草稿状态的题集可以修改名称");
        }
        if (mapper.updateCollectionName(cid, name) != 1) throw notFound("COLLECTION_NOT_FOUND", "题集不存在");
        CollectionRenameVo result = new CollectionRenameVo(String.valueOf(cid), name);
        finishRenameIdempotency(idemId, cid, result, "题集名称已更新");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectionMutationVo deleteDraft(String revisionId, String requestId) {
        long rid = id(revisionId, "revisionId");
        UUID.fromString(requestId);
        String hash = hash("delete:" + rid);
        CollectionMutationVo replay = replay("delete_collection_revision", requestId, hash, rid);
        if (replay != null) return replay;
        long idemId = startIdempotency("delete_collection_revision", requestId, hash, rid);
        CollectionRevisionRow revision = mapper.lockRevision(rid);
        if (revision == null) throw notFound("COLLECTION_REVISION_NOT_FOUND", "题集修订不存在");
        requireEditableStatus(revision);
        int revisionCount = mapper.countRevisions(revision.getCollectionId());
        mapper.deleteItems(rid);
        if (mapper.deleteDraftRevision(rid) != 1) throw statusConflict();
        if (revisionCount == 1) mapper.deleteCollection(revision.getCollectionId());
        CollectionMutationVo result = mutation(revision.getCollectionId(), revision.getCollectionCode(), rid,
            revision.getRevisionNo(), revision.getStatus(), revision.getRowVersion(), false);
        finishIdempotency(idemId, rid, result, "题集草稿已删除");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectionMutationVo createRevision(String collectionId, String requestId, CollectionRevisionCreateBo command) {
        long cid = id(collectionId, "collectionId");
        UUID.fromString(requestId);
        if (mapper.lockCollection(cid) == null) throw notFound("COLLECTION_NOT_FOUND", "题集不存在");
        CollectionRevisionRow pendingReview = mapper.selectPendingReviewRevision(cid);
        if (pendingReview != null) throw pendingReviewExists(pendingReview);
        boolean defaultSource = command == null || trim(command.getSourceRevisionId()) == null;
        CollectionRevisionRow source = defaultSource ? mapper.selectCurrentPublished(cid)
            : mapper.selectRevision(id(command.getSourceRevisionId(), "sourceRevisionId"));
        if (source == null) {
            if (defaultSource) throw invalid("sourceRevisionId不能为空且当前题集不存在已发布修订");
            throw notFound("COLLECTION_REVISION_NOT_FOUND", "来源修订不存在");
        }
        if (!Objects.equals(source.getCollectionId(), cid)) {
            throw conflict("COLLECTION_REVISION_SOURCE_INVALID", "来源修订不属于当前题集");
        }
        String hash = hash("create-revision:" + cid + ":source:" + source.getId());
        CollectionMutationVo replay = replay("create_collection_revision", requestId, hash, cid);
        if (replay != null) return replay;
        long idemId = startIdempotency("create_collection_revision", requestId, hash, cid);
        int revisionNo = mapper.nextRevisionNo(cid);
        long targetId = IdUtil.getSnowflakeNextId();
        mapper.insertRevision(targetId, cid, revisionNo, source.getCollectionName(), source.getCollectionType(),
            source.getCertificationId(), source.getSyllabusVersionId(), source.getDurationMinutes(),
            source.getQuestionCount(), source.getTotalReportScore());
        for (CollectionItemRow item : mapper.selectItems(source.getId())) {
            mapper.insertItem(IdUtil.getSnowflakeNextId(), targetId, item.getQuestionRevisionId(),
                item.getItemOrder(), item.getReportScore(), item.getAnswerSchema());
        }
        CollectionMutationVo result = mutation(cid, source.getCollectionCode(), targetId, revisionNo,
            "draft", 0, true, String.valueOf(source.getId()));
        finishIdempotency(idemId, cid, result, "题集新修订已创建");
        return result;
    }

    private CollectionPublishCheckVo publishCheck(CollectionRevisionRow revision) {
        return publishCheck(revision, mapper.selectItems(revision.getId()), false);
    }

    private CollectionPublishCheckVo publishCheck(CollectionRevisionRow revision, List<CollectionItemRow> items,
                                                   boolean allowDraftQuestions) {
        List<CollectionPublishCheckVo.IssueVo> issues = new ArrayList<>();
        if (revision.getCollectionName() == null || revision.getCollectionName().isBlank()) issue(issues, "COLLECTION_NAME_REQUIRED", "题集名称不能为空", null);
        if (!TYPES.contains(revision.getCollectionType())) issue(issues, "COLLECTION_TYPE_INVALID", "题集类型无效", null);
        if (mapper.countCertification(revision.getCertificationId()) != 1 || mapper.countSyllabusInCertification(
            revision.getSyllabusVersionId(), revision.getCertificationId()) != 1) {
            issue(issues, "COLLECTION_SCOPE_INVALID", "资格或考纲归属无效", null);
        }
        if (items.isEmpty()) issue(issues, "COLLECTION_QUESTION_REQUIRED", "题集至少需要一道题目", null);
        Set<Long> questions = new HashSet<>();
        Set<Long> subjects = new HashSet<>();
        for (int index = 0; index < items.size(); index++) {
            CollectionItemRow item = items.get(index);
            if (item.getItemOrder() != index + 1) issue(issues, "ITEM_ORDER_INVALID", "题序必须从1连续", item.getItemOrder());
            if (!questions.add(item.getQuestionRevisionId())) issue(issues, "QUESTION_REVISION_DUPLICATED", "题目修订重复", item.getItemOrder());
            if (item.getReportScore() == null || item.getReportScore().signum() <= 0) issue(issues, "REPORT_SCORE_INVALID", "报告分值必须大于0", item.getItemOrder());
            if ("FIRST_DIAGNOSTIC".equals(revision.getCollectionType())) {
                if (!"CHOICE".equals(item.getQuestionType())) issue(issues, "FIRST_DIAGNOSTIC_CHOICE_REQUIRED", "首次诊断只允许选择题", item.getItemOrder());
                if (item.getAnswerSchema() == null || item.getAnswerSchema().isBlank()) issue(issues, "FIRST_DIAGNOSTIC_SCORING_FACT_MISSING", "首次诊断缺少评分事实", item.getItemOrder());
                if (item.getEstimatedSeconds() == null || item.getEstimatedSeconds() <= 0) issue(issues, "FIRST_DIAGNOSTIC_STANDARD_TIME_MISSING", "首次诊断缺少标准作答时间", item.getItemOrder());
                if (item.getInvalidKnowledgeCount() == null || item.getInvalidKnowledgeCount() > 0) issue(issues, "FIRST_DIAGNOSTIC_LEAF_KNOWLEDGE_REQUIRED", "首次诊断题目必须映射有效叶子知识点", item.getItemOrder());
                if (item.getMissingLeafImportanceCount() == null || item.getMissingLeafImportanceCount() > 0) issue(issues, "FIRST_DIAGNOSTIC_LEAF_IMPORTANCE_REQUIRED", "首次诊断叶子知识点必须配置重要度1、2或3", item.getItemOrder());
            }
            if (!"published".equals(item.getStatus())
                && !(allowDraftQuestions && Set.of("draft", "rejected", "pending_review").contains(item.getStatus()))) {
                String code = "rejected".equals(item.getStatus()) ? "QUESTION_REJECTED"
                    : "pending_review".equals(item.getStatus()) ? "QUESTION_PENDING_REVIEW" : "QUESTION_NOT_PUBLISHED";
                issue(issues, code, "题目修订状态不允许题集提交或发布", item.getItemOrder());
            }
            if (!Objects.equals(item.getCertificationId(), revision.getCertificationId())
                || (item.getSyllabusVersionId() != null
                    && !Objects.equals(item.getSyllabusVersionId(), revision.getSyllabusVersionId()))) {
                issue(issues, "QUESTION_SCOPE_MISMATCH", "题目不属于题集资格或考纲", item.getItemOrder());
            }
            if (item.getExamSubjectId() != null) subjects.add(item.getExamSubjectId());
        }
        if ("FIRST_DIAGNOSTIC".equals(revision.getCollectionType())) {
            if (items.size() < 10) issue(issues, "FIRST_DIAGNOSTIC_QUESTION_COUNT", "首次诊断题目数量不得少于10题，当前为" + items.size() + "题", null);
            Set<Long> requiredSubjects = new HashSet<>(mapper.selectActiveSubjectIds(revision.getCertificationId()));
            if (requiredSubjects.size() != 3 || !subjects.equals(requiredSubjects)) {
                issue(issues, "FIRST_DIAGNOSTIC_SUBJECT_COVERAGE", "首次诊断必须覆盖资格下三个考试科目", null);
            }
        }
        return new CollectionPublishCheckVo(issues.isEmpty(), issues, List.of(),
            new CollectionPublishCheckVo.SummaryVo(items.size(), revision.getTotalReportScore(), subjects.size()));
    }

    private CollectionPublishCheckVo submitCheck(CollectionRevisionRow revision, List<CollectionItemRow> items,
                                                  Map<Long, QuestionRows.Revision> questions) {
        CollectionPublishCheckVo base = publishCheck(revision, items, true);
        List<CollectionPublishCheckVo.IssueVo> issues = new ArrayList<>(base.blockingIssues());
        for (CollectionItemRow item : items) {
            QuestionRows.Revision question = questions.get(item.getQuestionRevisionId());
            if (question == null) {
                issue(issues, "QUESTION_REVISION_NOT_VISIBLE", "题目修订不存在或当前用户不可访问", item.getItemOrder());
                continue;
            }
            if (!Set.of("draft", "rejected").contains(question.getStatus())) continue;
            for (QuestionErrorVo.BlockingIssueVo questionIssue : reviewSubmissionSupport.validate(question)) {
                String message = questionIssue.fieldPath() == null || questionIssue.fieldPath().isBlank()
                    ? questionIssue.message() : questionIssue.fieldPath() + "：" + questionIssue.message();
                issue(issues, questionIssue.code(), message, item.getItemOrder());
            }
        }
        return new CollectionPublishCheckVo(issues.isEmpty(), issues, base.warnings(), base.summary());
    }

    private CollectionPublishCheckVo approvalCheck(CollectionRevisionRow revision, List<CollectionItemRow> items,
                                                    List<QuestionRows.Revision> lockedQuestions) {
        CollectionPublishCheckVo base = publishCheck(revision, items, false);
        Set<Long> lockedRevisionIds = lockedQuestions.stream().map(QuestionRows.Revision::getRevisionId)
            .collect(java.util.stream.Collectors.toSet());
        List<CollectionPublishCheckVo.IssueVo> issues = new ArrayList<>(base.blockingIssues());
        for (CollectionItemRow item : items) {
            if (!lockedRevisionIds.contains(item.getQuestionRevisionId())) {
                issue(issues, "QUESTION_REVISION_NOT_VISIBLE", "题目修订不存在或已删除", item.getItemOrder());
            }
        }
        return new CollectionPublishCheckVo(issues.isEmpty(), issues, base.warnings(), base.summary());
    }

    private static boolean sameItems(List<CollectionItemRow> before, List<CollectionItemRow> after) {
        if (before.size() != after.size()) return false;
        for (int index = 0; index < before.size(); index++) {
            CollectionItemRow left = before.get(index);
            CollectionItemRow right = after.get(index);
            if (!Objects.equals(left.getItemOrder(), right.getItemOrder())
                || !Objects.equals(left.getQuestionRevisionId(), right.getQuestionRevisionId())
                || !Objects.equals(left.getReportScore(), right.getReportScore())
                || !Objects.equals(left.getAnswerSchema(), right.getAnswerSchema())) {
                return false;
            }
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectionMutationVo submitReview(String revisionId, String requestId) {
        long rid = id(revisionId, "revisionId");
        UUID.fromString(requestId);
        String hash = hash("submit:" + rid);
        CollectionMutationVo replay = replay("submit_collection_review", requestId, hash, rid);
        if (replay != null) return replay;
        CollectionRevisionRow initial = mapper.selectRevision(rid);
        if (initial == null) throw notFound("COLLECTION_REVISION_NOT_FOUND", "题集修订不存在");
        List<CollectionItemRow> initialItems = mapper.selectItems(rid);
        List<Long> questionRevisionIds = initialItems.stream().map(CollectionItemRow::getQuestionRevisionId)
            .distinct().sorted().toList();
        List<QuestionRows.Revision> lockedQuestions = reviewSubmissionSupport.lockVisibleRevisions(
            questionRevisionIds, visibleUserId());
        if (mapper.lockCollection(initial.getCollectionId()) == null) throw notFound("COLLECTION_NOT_FOUND", "题集不存在");
        CollectionRevisionRow revision = mapper.lockRevision(rid);
        if (revision == null) throw notFound("COLLECTION_REVISION_NOT_FOUND", "题集修订不存在");
        CollectionMutationVo lockedReplay = replay("submit_collection_review", requestId, hash, rid);
        if (lockedReplay != null) return lockedReplay;
        List<CollectionItemRow> items = mapper.selectItems(rid);
        if (!sameItems(initialItems, items)) throw collectionChanged();
        requireEditableStatus(revision);
        CollectionRevisionRow pendingReview = mapper.selectPendingReviewRevision(revision.getCollectionId());
        if (pendingReview != null && !Objects.equals(pendingReview.getId(), rid)) throw pendingReviewExists(pendingReview);
        Map<Long, QuestionRows.Revision> questions = lockedQuestions.stream().collect(java.util.stream.Collectors.toMap(
            QuestionRows.Revision::getRevisionId, question -> question));
        CollectionPublishCheckVo check = submitCheck(revision, items, questions);
        if (!check.passed()) {
            throw new CollectionException(422, "COLLECTION_PUBLISH_CHECK_FAILED", "发布检查未通过", check.blockingIssues());
        }
        long idemId = startIdempotency("submit_collection_review", requestId, hash, rid);
        Long userId = LoginHelper.getUserId();
        String traceId = UUID.randomUUID().toString();
        for (QuestionRows.Revision question : lockedQuestions) {
            if (Set.of("draft", "rejected").contains(question.getStatus())) {
                reviewSubmissionSupport.submitDraft(question, userId, requestId, traceId);
            }
        }
        if (mapper.markSubmitted(rid, userId) != 1) throw transitionConflict(rid);
        audit("submit_collection_review", revision, revision.getStatus(), "pending_review", requestId);
        CollectionMutationVo result = mutation(revision.getCollectionId(), revision.getCollectionCode(), rid,
            revision.getRevisionNo(), "pending_review", revision.getRowVersion() + 1, false);
        finishIdempotency(idemId, rid, result, "题集已提交审核");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectionMutationVo approve(String revisionId, String requestId) {
        long rid = id(revisionId, "revisionId");
        UUID.fromString(requestId);
        String hash = hash("approve:" + rid);
        CollectionMutationVo replay = replay("approve_collection", requestId, hash, rid);
        if (replay != null) return replay;
        CollectionRevisionRow initial = mapper.selectRevision(rid);
        if (initial == null) throw notFound("COLLECTION_REVISION_NOT_FOUND", "题集修订不存在");
        List<CollectionItemRow> initialItems = mapper.selectItems(rid);
        List<QuestionRows.Revision> lockedQuestions = reviewSubmissionSupport.lockVisibleRevisions(
            initialItems.stream().map(CollectionItemRow::getQuestionRevisionId)
            .distinct().sorted().toList(), null);
        if (mapper.lockCollection(initial.getCollectionId()) == null) throw notFound("COLLECTION_NOT_FOUND", "题集不存在");
        CollectionRevisionRow revision = mapper.lockRevision(rid);
        CollectionMutationVo lockedReplay = replay("approve_collection", requestId, hash, rid);
        if (lockedReplay != null) return lockedReplay;
        requireStatus(revision, "pending_review");
        List<CollectionItemRow> items = mapper.selectItems(rid);
        if (!sameItems(initialItems, items)) throw collectionChanged();
        CollectionPublishCheckVo check = approvalCheck(revision, items, lockedQuestions);
        if (!check.passed()) {
            throw new CollectionException(422, "COLLECTION_PUBLISH_CHECK_FAILED", "发布检查未通过", check.blockingIssues());
        }
        long idemId = startIdempotency("approve_collection", requestId, hash, rid);
        List<CollectionRevisionRow> previousPublished = mapper.lockPublishedRevisions(revision.getCollectionId());
        for (CollectionRevisionRow previous : previousPublished) {
            if (mapper.markPublishedAsDraft(previous.getId()) != 1) throw transitionConflict(previous.getId());
            audit("supersede_collection_revision", previous, "published", "draft", requestId);
        }
        if (mapper.markPublished(rid, LoginHelper.getUserId()) != 1) throw transitionConflict(rid);
        mapper.upsertCurrent(revision.getCollectionId(), rid);
        audit("approve_collection_review", revision, "pending_review", "published", requestId);
        audit("publish_collection_revision", revision, "pending_review", "published", requestId);
        audit("switch_collection_current", revision, null, "published", requestId);
        CollectionMutationVo result = mutation(revision.getCollectionId(), revision.getCollectionCode(), rid,
            revision.getRevisionNo(), "published", revision.getRowVersion() + 1, false);
        finishIdempotency(idemId, rid, result, "题集已审核并发布");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectionMutationVo offline(String revisionId, String requestId) {
        long rid = id(revisionId, "revisionId");
        UUID.fromString(requestId);
        String hash = hash("offline:" + rid);
        CollectionMutationVo replay = replay("offline_collection_revision", requestId, hash, rid);
        if (replay != null) return replay;
        long idemId = startIdempotency("offline_collection_revision", requestId, hash, rid);
        CollectionRevisionRow initial = mapper.selectRevision(rid);
        if (initial == null) throw notFound("COLLECTION_REVISION_NOT_FOUND", "题集修订不存在");
        if (mapper.lockCollection(initial.getCollectionId()) == null) throw notFound("COLLECTION_NOT_FOUND", "题集不存在");
        CollectionRevisionRow revision = mapper.lockRevision(rid);
        CollectionRevisionRow current = mapper.lockCurrentPublished(revision.getCollectionId());
        if (!"published".equals(revision.getStatus()) || current == null || !Objects.equals(current.getId(), rid)) {
            throw conflict("COLLECTION_OFFLINE_INVALID_STATE", "下架目标不是当前已发布修订");
        }
        if (mapper.deleteCurrent(revision.getCollectionId(), rid) != 1) {
            throw conflict("COLLECTION_OFFLINE_INVALID_STATE", "下架目标不是当前已发布修订");
        }
        if (mapper.markPublishedAsDraft(rid) != 1) throw transitionConflict(rid);
        audit("offline_collection_revision", revision, "published", "draft", requestId);
        CollectionMutationVo result = mutation(revision.getCollectionId(), revision.getCollectionCode(), rid,
            revision.getRevisionNo(), "draft", revision.getRowVersion() + 1, false);
        finishIdempotency(idemId, rid, result, "题集已下架并退回草稿");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectionMutationVo reject(String revisionId, String requestId, String reviewOpinion) {
        long rid = id(revisionId, "revisionId");
        UUID.fromString(requestId);
        String opinion = reviewOpinion == null ? null : reviewOpinion.trim();
        if (opinion == null || opinion.isEmpty() || opinion.length() > 500) throw invalid("审核意见不能为空且不能超过500字符");
        String hash = hash("reject:" + rid + "\n" + opinion);
        CollectionMutationVo replay = replay("reject_collection", requestId, hash, rid);
        if (replay != null) return replay;
        long idemId = startIdempotency("reject_collection", requestId, hash, rid);
        CollectionRevisionRow revision = mapper.lockRevision(rid);
        if (revision == null) throw notFound("COLLECTION_REVISION_NOT_FOUND", "题集修订不存在");
        requireStatus(revision, "pending_review");
        if (mapper.markRejected(rid, LoginHelper.getUserId(), opinion) != 1) throw transitionConflict(rid);
        audit("reject_collection_review", revision, "pending_review", "rejected", requestId);
        CollectionMutationVo result = mutation(revision.getCollectionId(), revision.getCollectionCode(), rid,
            revision.getRevisionNo(), "rejected", revision.getRowVersion() + 1, false);
        finishIdempotency(idemId, rid, result, "题集已驳回");
        return result;
    }

    private CollectionRevisionDetailVo detailVo(CollectionRevisionRow revision, boolean hasWorkingRevision) {
        List<CollectionRevisionDetailVo.ItemVo> items = mapper.selectItems(revision.getId()).stream()
            .map(item -> new CollectionRevisionDetailVo.ItemVo(item.getItemOrder(), String.valueOf(item.getQuestionRevisionId()),
                String.valueOf(item.getQuestionId()), item.getQuestionCode(), item.getStem(), item.getQuestionType(),
                item.getDifficulty(), item.getStatus(), string(item.getExamSubjectId()), item.getExamSubjectName(),
                string(item.getKnowledgePointId()), item.getKnowledgePointLabel(), item.getReportScore())).toList();
        return new CollectionRevisionDetailVo(String.valueOf(revision.getId()), String.valueOf(revision.getCollectionId()),
            revision.getCollectionCode(), revision.getRevisionNo(), revision.getCollectionName(), revision.getCollectionType(),
            String.valueOf(revision.getCertificationId()), revision.getCertificationName(),
            String.valueOf(revision.getSyllabusVersionId()), revision.getSyllabusVersionName(), revision.getDurationMinutes(),
            Boolean.TRUE.equals(revision.getPauseAllowed()), revision.getStatus(), revision.getQuestionCount(),
            revision.getTotalReportScore(), String.valueOf(revision.getRowVersion()), reviewOpinion(revision),
            actions(revision.getStatus(), hasWorkingRevision), items, revision.getUpdatedTime());
    }

    private ValidatedItems validateItems(CollectionSaveBo command, long certificationId, long syllabusId) {
        if (mapper.countCertification(certificationId) != 1) throw invalid("certificationId不存在");
        if (mapper.countSyllabusInCertification(syllabusId, certificationId) != 1) throw invalid("考纲不属于所选资格");
        Set<Long> ids = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (int index = 0; index < command.getItems().size(); index++) {
            CollectionSaveBo.ItemBo item = command.getItems().get(index);
            if (item.getItemOrder() != index + 1) throw invalid("题序必须从1连续");
            long questionRevisionId = id(item.getQuestionRevisionId(), "questionRevisionId");
            if (!ids.add(questionRevisionId)) throw invalid("同一题目修订不能重复加入题集");
            total = total.add(item.getReportScore());
        }
        if (ids.isEmpty()) return new ValidatedItems(Map.of(), total);
        List<CollectionItemRow> rows = mapper.selectQuestionRevisionMetadata(ids, visibleUserId());
        if (rows.size() != ids.size()) throw invalid("存在无效的题目修订");
        Map<Long, CollectionItemRow> metadata = new HashMap<>();
        for (CollectionItemRow row : rows) {
            if (!Set.of("draft", "pending_review", "rejected", "published").contains(row.getStatus())) {
                throw invalid("题目修订状态无效");
            }
            if (!Objects.equals(row.getCertificationId(), certificationId)
                || (row.getSyllabusVersionId() != null && !Objects.equals(row.getSyllabusVersionId(), syllabusId))) {
                throw invalid("题目修订不属于题集资格或考纲");
            }
            if (row.getAnswerSchema() == null || row.getAnswerSchema().isBlank()) throw invalid("题目修订缺少答题结构");
            metadata.put(row.getQuestionRevisionId(), row);
        }
        return new ValidatedItems(metadata, total);
    }

    private void insertItems(long revisionId, CollectionSaveBo command, Map<Long, CollectionItemRow> metadata) {
        for (CollectionSaveBo.ItemBo item : command.getItems()) {
            long questionRevisionId = id(item.getQuestionRevisionId(), "questionRevisionId");
            mapper.insertItem(IdUtil.getSnowflakeNextId(), revisionId, questionRevisionId,
                item.getItemOrder(), item.getReportScore(), metadata.get(questionRevisionId).getAnswerSchema());
        }
    }

    private void validateBasic(CollectionSaveBo command, boolean requireVersion) {
        command.setCollectionName(command.getCollectionName() == null ? null : command.getCollectionName().trim());
        command.setCollectionType(command.getCollectionType() == null ? null : command.getCollectionType().trim());
        if (command.getCollectionName() == null || command.getCollectionName().isEmpty() || command.getCollectionName().length() > 200) throw invalid("题集名称不能为空且不能超过200字符");
        if (!TYPES.contains(command.getCollectionType())) throw invalid("collectionType非法");
        if (command.getDurationMinutes() == null || command.getDurationMinutes() <= 0) throw invalid("durationMinutes必须大于0");
        if ("PAST_PAPER".equals(command.getCollectionType())) {
            command.setPaperTypeCode(command.getPaperTypeCode() == null ? null : command.getPaperTypeCode().trim());
            command.setPaperTypeName(command.getPaperTypeName() == null ? null : command.getPaperTypeName().trim());
            if (command.getExamYear() == null || command.getExamYear() < 1990 || command.getExamYear() > 2100
                || command.getExamMonth() == null || !Set.of(5, 11).contains(command.getExamMonth())
                || command.getPaperTypeCode() == null || command.getPaperTypeCode().isBlank()
                || command.getPaperTypeName() == null || command.getPaperTypeName().isBlank()) {
                throw invalid("PAST_PAPER必须填写年份、5月或11月批次、试卷类型编码和名称");
            }
        }
        if (command.getItems() == null) throw invalid("items不能为空");
        if (requireVersion && (command.getRowVersion() == null || command.getRowVersion().isBlank())) throw invalid("保存草稿必须提供rowVersion");
    }

    private long startIdempotency(String action, String requestId, String payloadHash, Long resourceId) {
        long id = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(id, action, requestId, payloadHash, resourceId, OffsetDateTime.now().plusDays(1)) == 1) return id;
        CollectionMutationVo concurrent = replay(action, requestId, payloadHash, resourceId);
        if (concurrent != null) throw conflict("COLLECTION_IDEMPOTENCY_REPLAY", "幂等请求已完成，请重试读取结果");
        throw conflict("COLLECTION_OPERATION_IN_PROGRESS", "相同请求正在处理中");
    }

    private CollectionMutationVo replay(String action, String requestId, String payloadHash, Long resourceId) {
        CollectionIdempotencyRow record = mapper.selectIdempotency(action, requestId);
        if (record == null) return null;
        if (!Objects.equals(record.getPayloadHash(), payloadHash) ||
            (resourceId != null && record.getResourceId() != null && !Objects.equals(record.getResourceId(), resourceId))) {
            throw invalid("X-Request-Id已被其他请求使用");
        }
        if (!"succeeded".equals(record.getStatus())) throw conflict("COLLECTION_OPERATION_IN_PROGRESS", "相同请求正在处理中");
        try {
            JsonNode root = jsonMapper.readTree(record.getResponseBody());
            return jsonMapper.treeToValue(root.path("response").path("data"), CollectionMutationVo.class);
        } catch (Exception exception) {
            throw new CollectionException(500, "COLLECTION_OPERATION_FAILURE", "读取幂等结果失败", exception);
        }
    }

    private void finishIdempotency(long idemId, long resourceId, CollectionMutationVo result, String message) {
        mapper.completeIdempotency(idemId, resourceId, 200, responseEnvelope(message, result));
    }

    private long startRenameIdempotency(String action, String requestId, String payloadHash, long resourceId) {
        long id = IdUtil.getSnowflakeNextId();
        if (mapper.insertIdempotency(id, action, requestId, payloadHash, resourceId,
            OffsetDateTime.now().plusDays(1)) == 1) {
            return id;
        }
        CollectionRenameVo concurrent = replayRename(action, requestId, payloadHash, resourceId);
        if (concurrent != null) throw conflict("COLLECTION_IDEMPOTENCY_REPLAY", "幂等请求已完成，请重试读取结果");
        throw conflict("COLLECTION_OPERATION_IN_PROGRESS", "相同请求正在处理中");
    }

    private CollectionRenameVo replayRename(String action, String requestId, String payloadHash, long resourceId) {
        CollectionIdempotencyRow record = mapper.selectIdempotency(action, requestId);
        if (record == null) return null;
        if (!Objects.equals(record.getPayloadHash(), payloadHash) ||
            (record.getResourceId() != null && !Objects.equals(record.getResourceId(), resourceId))) {
            throw invalid("X-Request-Id已被其他请求使用");
        }
        if (!"succeeded".equals(record.getStatus())) {
            throw conflict("COLLECTION_OPERATION_IN_PROGRESS", "相同请求正在处理中");
        }
        try {
            JsonNode root = jsonMapper.readTree(record.getResponseBody());
            return jsonMapper.treeToValue(root.path("response").path("data"), CollectionRenameVo.class);
        } catch (Exception exception) {
            throw new CollectionException(500, "COLLECTION_OPERATION_FAILURE", "读取幂等结果失败", exception);
        }
    }

    private void finishRenameIdempotency(long idemId, long resourceId, CollectionRenameVo result, String message) {
        mapper.completeIdempotency(idemId, resourceId, 200, responseEnvelope(message, result));
    }

    private void audit(String action, CollectionRevisionRow revision, String beforeStatus,
                       String afterStatus, String requestId) {
        mapper.insertAudit(IdUtil.getSnowflakeNextId(), LoginHelper.getUserId(), action, revision.getId(),
            auditSnapshot(revision, beforeStatus, revision.getRowVersion()),
            auditSnapshot(revision, afterStatus, revision.getRowVersion() + 1), requestId);
    }

    private String auditSnapshot(CollectionRevisionRow revision, String status, long rowVersion) {
        Map<String, Object> snapshot = VersionedJsonDocumentFactory.flatFields(
            QuestionJsonSchema.COLLECTION_SNAPSHOT, Map.of());
        snapshot.put("collection_id", String.valueOf(revision.getCollectionId()));
        snapshot.put("revision_id", String.valueOf(revision.getId()));
        snapshot.put("revision_no", revision.getRevisionNo());
        snapshot.put("status", status);
        snapshot.put("row_version", String.valueOf(rowVersion));
        return write(snapshot);
    }

    private CollectionRevisionRow requireRevision(long revisionId) {
        CollectionRevisionRow revision = mapper.selectRevision(revisionId);
        if (revision == null) throw notFound("COLLECTION_REVISION_NOT_FOUND", "题集修订不存在");
        return revision;
    }

    private void requireStatus(CollectionRevisionRow revision, String status) {
        if (revision == null || !status.equals(revision.getStatus())) throw statusConflict();
    }

    private void requireEditableStatus(CollectionRevisionRow revision) {
        if (revision == null || !Set.of("draft", "rejected").contains(revision.getStatus())) throw statusConflict();
    }

    private CollectionListVo toListVo(CollectionListRow row) {
        return new CollectionListVo(row.getCollectionId(), row.getCollectionCode(), row.getRevisionId(), row.getRevisionNo(),
            row.getCollectionName(), row.getCollectionType(), row.getCertificationId(), row.getCertificationName(),
            row.getSyllabusVersionId(), row.getSyllabusVersionName(), row.getStatus(), Boolean.TRUE.equals(row.getCurrentPublished()),
            Boolean.TRUE.equals(row.getHasReviewOpinion()), String.valueOf(row.getRowVersion()), row.getQuestionCount(), row.getTotalReportScore(),
            row.getDurationMinutes(), row.getUpdatedTime());
    }

    private CollectionDetailVo.RevisionSummaryVo summary(CollectionRevisionRow row, CollectionRevisionRow published) {
        return new CollectionDetailVo.RevisionSummaryVo(String.valueOf(row.getId()), row.getRevisionNo(), row.getStatus(),
            reviewOpinion(row) != null, published != null && Objects.equals(row.getId(), published.getId()), String.valueOf(row.getRowVersion()),
            row.getQuestionCount(), row.getTotalReportScore(), row.getUpdatedTime());
    }

    private static List<String> actions(String status, boolean hasWorkingRevision) {
        return switch (status) {
            case "draft", "rejected" -> List.of("view", "edit", "submit_review", "delete");
            case "pending_review" -> List.of("view");
            case "published" -> hasWorkingRevision ? List.of("view") : List.of("view", "create_revision");
            default -> List.of("view");
        };
    }

    private static void normalizeQuery(CollectionQueryBo query) {
        query.setKeyword(trim(query.getKeyword()));
        query.setCollectionType(trim(query.getCollectionType()));
        query.setStatus(trim(query.getStatus()));
        if (query.getKeyword() != null && query.getKeyword().length() > 200) throw invalid("keyword长度不能超过200字符");
        if (query.getCollectionType() != null && !TYPES.contains(query.getCollectionType())) throw invalid("collectionType非法");
        if (query.getStatus() != null && !LIST_STATUSES.contains(query.getStatus())) throw invalid("status非法");
    }

    private static void normalizeManageQuery(CollectionQueryBo query) {
        normalizeQuery(query);
        if (query.getStatuses() == null) return;
        List<String> statuses = query.getStatuses().stream().map(CollectionServiceImpl::trim)
            .filter(Objects::nonNull).distinct().toList();
        if (!Set.of("rejected", "draft", "pending_review", "published").containsAll(statuses)) {
            throw invalid("statuses非法");
        }
        query.setStatuses(statuses);
    }

    private static CollectionMutationVo mutation(long collectionId, String code, long revisionId, int revisionNo,
                                                  String status, long rowVersion, boolean createdRevision) {
        return mutation(collectionId, code, revisionId, revisionNo, status, rowVersion, createdRevision, null);
    }
    private static CollectionMutationVo mutation(long collectionId, String code, long revisionId, int revisionNo,
                                                  String status, long rowVersion, boolean createdRevision,
                                                  String sourceRevisionId) {
        return new CollectionMutationVo(String.valueOf(collectionId), code, String.valueOf(revisionId), revisionNo,
            status, String.valueOf(rowVersion), createdRevision, sourceRevisionId);
    }
    private static String collectionCode(long id) {
        String digits = Long.toUnsignedString(id);
        return "COL-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + digits.substring(Math.max(0, digits.length() - 8));
    }
    private static String string(Long value) { return value == null ? null : String.valueOf(value); }
    private static Long visibleUserId() { return LoginHelper.isSuperAdmin() ? null : LoginHelper.getUserId(); }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String responseEnvelope(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 200); response.put("msg", message); response.put("data", data);
        return VersionedJsonDocumentFactory.json(jsonMapper, QuestionJsonSchema.OPERATION_RESPONSE,
            Map.of("response", response));
    }
    private String write(Object value) {
        try { return jsonMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new CollectionException(500, "COLLECTION_OPERATION_FAILURE", "序列化题集数据失败", exception); }
    }
    private static String hash(String value) { return DigestUtil.sha256Hex(value); }
    private static long id(String value, String field) {
        try {
            if (value == null || !value.matches("^[1-9][0-9]{0,18}$")) throw new NumberFormatException();
            return Long.parseLong(value);
        } catch (NumberFormatException exception) { throw invalid(field + "必须是十进制正整数"); }
    }
    private static Long nullableId(String value, String field) { return value == null || value.isBlank() ? null : id(value, field); }
    private static long nonNegative(String value, String field) {
        try { long parsed = Long.parseLong(value); if (parsed < 0) throw new NumberFormatException(); return parsed; }
        catch (NumberFormatException exception) { throw invalid(field + "必须是非负整数"); }
    }
    private static void issue(List<CollectionPublishCheckVo.IssueVo> issues, String code, String message, Integer order) {
        issues.add(new CollectionPublishCheckVo.IssueVo(code, message, order));
    }
    private static String reviewOpinion(CollectionRevisionRow revision) {
        return "rejected".equals(revision.getStatus()) ? trim(revision.getReviewOpinion()) : null;
    }

    private static CollectionException collectionChanged() {
        return conflict("COLLECTION_ITEMS_CONCURRENTLY_CHANGED", "题集题目清单已被其他操作更新，请刷新后重试");
    }
    private static CollectionException pendingReviewExists(CollectionRevisionRow revision) {
        return new CollectionException(409, "COLLECTION_PENDING_REVIEW_EXISTS", "题集已有审核中修订", null, null,
            String.valueOf(revision.getId()));
    }
    private static CollectionException invalid(String message) { return new CollectionException(400, "COLLECTION_REQUEST_INVALID", message); }
    private static CollectionException conflict(String code, String message) { return new CollectionException(409, code, message); }
    private static CollectionException notFound(String code, String message) { return new CollectionException(404, code, message); }
    private static CollectionException statusConflict() { return conflict("COLLECTION_REVISION_STATE_CONFLICT", "当前题集修订状态不允许此操作"); }
    private CollectionException transitionConflict(long revisionId) {
        return mapper.selectRevision(revisionId) == null
            ? notFound("COLLECTION_REVISION_NOT_FOUND", "题集修订不存在")
            : statusConflict();
    }
    private static CollectionException versionConflict(CollectionRevisionRow revision) {
        return new CollectionException(409, "COLLECTION_REVISION_VERSION_CONFLICT", "题集草稿已被其他人更新，请刷新后重试",
            String.valueOf(revision.getId()), String.valueOf(revision.getRowVersion()), null);
    }

    private record ValidatedItems(Map<Long, CollectionItemRow> metadata, BigDecimal totalScore) { }

    private static final class CollectionManageBuilder {
        private final CollectionListRow first;
        private final List<CollectionManageVo.RevisionSummaryVo> revisions = new ArrayList<>();
        private CollectionListRow currentPublished;
        private OffsetDateTime updatedTime;

        private CollectionManageBuilder(CollectionListRow first) {
            this.first = first;
            this.updatedTime = first.getCollectionUpdatedTime();
        }

        private void add(CollectionListRow row) {
            revisions.add(new CollectionManageVo.RevisionSummaryVo(row.getRevisionId(), row.getRevisionNo(), row.getStatus(),
                Boolean.TRUE.equals(row.getHasReviewOpinion()), Boolean.TRUE.equals(row.getCurrentPublished()),
                String.valueOf(row.getRowVersion()), row.getQuestionCount(), row.getTotalReportScore(), row.getUpdatedTime()));
            if (Boolean.TRUE.equals(row.getCurrentPublished())) currentPublished = row;
            if (updatedTime == null || row.getUpdatedTime().isAfter(updatedTime)) updatedTime = row.getUpdatedTime();
        }

        private CollectionManageVo build() {
            return new CollectionManageVo(first.getCollectionId(), first.getCollectionCode(), first.getParentCollectionName(),
                first.getCollectionType(), first.getCertificationId(), first.getCertificationName(), first.getSyllabusVersionId(),
                first.getSyllabusVersionName(), currentPublished == null ? null : currentPublished.getRevisionId(),
                currentPublished == null ? null : currentPublished.getRevisionNo(), updatedTime, revisions);
        }
    }
}
