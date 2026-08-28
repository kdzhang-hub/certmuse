package org.dromara.certmuse.question.service.impl;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.question.domain.QuestionRows;
import org.dromara.certmuse.question.domain.vo.QuestionErrorVo;
import org.dromara.certmuse.question.mapper.QuestionMapper;
import org.dromara.certmuse.question.support.QuestionException;
import org.dromara.certmuse.question.support.QuestionJsonSchema;
import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;
import org.dromara.certmuse.question.validation.QuestionSubmitReviewValidator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 题目提交审核的领域协作组件，供单题提交与题集联合提交复用。
 */
@Component
@RequiredArgsConstructor
public class QuestionReviewSubmissionSupport {
    private final QuestionMapper mapper;
    private final QuestionSubmitReviewValidator validator;
    private final JsonMapper jsonMapper;

    /** 按修订 ID 固定顺序锁定当前用户可见的题目修订。 */
    public List<QuestionRows.Revision> lockVisibleRevisions(Collection<Long> revisionIds, Long visibleUserId) {
        if (revisionIds.isEmpty()) return List.of();
        return mapper.lockReviewRevisions(revisionIds.stream().sorted().toList(), visibleUserId);
    }

    /** 执行与单题提交一致的完整发布门禁。 */
    public List<QuestionErrorVo.BlockingIssueVo> validate(QuestionRows.Revision revision) {
        long revisionId = revision.getRevisionId();
        List<QuestionRows.Knowledge> knowledge = mapper.selectKnowledge(revisionId);
        return validator.validate(revision, mapper.selectOptions(revisionId), knowledge,
            activeKnowledgeMetadata(knowledge));
    }

    /** 将已通过门禁的草稿或已驳回题目提交审核，并写入事件和业务审计。 */
    public void submitDraft(QuestionRows.Revision revision, Long userId, String requestId, String traceId) {
        if (mapper.markReviewSubmitted(revision.getRevisionId(), userId) != 1) {
            throw new QuestionException(409, "QUESTION_REVISION_CONFLICT", "题目修订已被其他操作更新，请重试");
        }
        mapper.insertRevisionEvent(IdUtil.getSnowflakeNextId(), revision.getQuestionId(), revision.getRevisionId(),
            "review_submitted", revision.getStatus(), "pending_review", null, userId, requestId, traceId);
        mapper.insertReviewAudit(IdUtil.getSnowflakeNextId(), userId, "review_submit", revision.getRevisionId(),
            write(auditData(revision, revision.getStatus(), null, requestId)),
            write(auditData(revision, "pending_review", userId, requestId)), traceId);
    }

    private Map<Long, QuestionRows.Knowledge> activeKnowledgeMetadata(List<QuestionRows.Knowledge> knowledge) {
        List<Long> ids = knowledge.stream().map(QuestionRows.Knowledge::getKnowledgePointId)
            .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return mapper.selectKnowledgeMetadata(ids).stream().collect(Collectors.toMap(
            QuestionRows.Knowledge::getKnowledgePointId, Function.identity(), (first, ignored) -> first,
            LinkedHashMap::new));
    }

    private static Map<String, Object> auditData(QuestionRows.Revision revision, String status,
                                                  Long submittedBy, String requestId) {
        Map<String, Object> data = VersionedJsonDocumentFactory.flatFields(
            QuestionJsonSchema.QUESTION_REVIEW_AUDIT, Map.of());
        data.put("content_type", "question_revision");
        data.put("aggregate_id", String.valueOf(revision.getQuestionId()));
        data.put("revision_id", String.valueOf(revision.getRevisionId()));
        data.put("status", status);
        data.put("request_id", requestId);
        if (submittedBy != null) data.put("submitted_by", String.valueOf(submittedBy));
        return data;
    }

    private String write(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new QuestionException(500, "QUESTION_OPERATION_FAILURE", "序列化题目审核数据失败", exception);
        }
    }
}
