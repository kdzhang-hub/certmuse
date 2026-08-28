package org.dromara.certmuse.question.validation;

import org.dromara.certmuse.question.domain.QuestionRows;
import org.dromara.certmuse.question.domain.vo.QuestionErrorVo;
import org.dromara.certmuse.shared.schema.SharedJsonSchema;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 校验题目草稿是否满足提交审核的发布门禁。
 */
@Component
public class QuestionSubmitReviewValidator {

    private static final Set<String> QUESTION_TYPES = Set.of("CHOICE", "CASE", "ESSAY");
    private static final Set<String> KNOWLEDGE_ROLES = Set.of("primary", "secondary");

    private final JsonMapper jsonMapper;

    public QuestionSubmitReviewValidator(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * 返回全部发布门禁问题，顺序稳定且不包含题目正文等敏感内容。
     */
    public List<QuestionErrorVo.BlockingIssueVo> validate(
        QuestionRows.Revision revision,
        List<QuestionRows.Option> options,
        List<QuestionRows.Knowledge> questionKnowledge,
        Map<Long, QuestionRows.Knowledge> activeKnowledgeMetadata
    ) {
        List<QuestionErrorVo.BlockingIssueVo> issues = new ArrayList<>();
        String questionType = revision.getQuestionType();
        if (!QUESTION_TYPES.contains(questionType)) {
            issue(issues, "QUESTION_TYPE_INVALID", "questionType", "题型必须为CHOICE、CASE或ESSAY");
        }
        if (revision.getStem() == null || revision.getStem().isBlank()) {
            issue(issues, "QUESTION_STEM_REQUIRED", "stem", "题干不能为空");
        }
        if ("CHOICE".equals(questionType)) {
            validateChoice(options, revision.getAnswer(), issues);
        } else if ("CASE".equals(questionType) || "ESSAY".equals(questionType)) {
            validateSubjective(options, revision.getAnswer(), issues);
        }
        validateQuestionKnowledge(revision, questionKnowledge, activeKnowledgeMetadata, issues);
        return List.copyOf(issues);
    }

    private void validateChoice(List<QuestionRows.Option> options, String answerJson,
                                List<QuestionErrorVo.BlockingIssueVo> issues) {
        if (options.size() < 2 || options.size() > 10) {
            issue(issues, "QUESTION_CHOICE_OPTIONS_INVALID", "options", "选择题选项数量必须为2至10项");
        }
        Set<String> labels = new HashSet<>();
        for (int index = 0; index < options.size(); index++) {
            QuestionRows.Option option = options.get(index);
            String path = "options[" + index + "]";
            if (option.getLabel() == null || option.getLabel().isBlank() || !labels.add(option.getLabel())) {
                issue(issues, "QUESTION_CHOICE_OPTIONS_INVALID", path + ".label", "选择题选项标签不能为空且不得重复");
            }
            if (!Objects.equals(option.getSortOrder(), index + 1)) {
                issue(issues, "QUESTION_CHOICE_OPTIONS_INVALID", path + ".sortOrder", "选择题选项顺序必须从1连续");
            }
        }
        JsonNode answer = answer(answerJson, issues, "QUESTION_CHOICE_ANSWER_INVALID", "answer");
        if (answer == null) {
            return;
        }
        if (!SharedJsonSchema.QUESTION_ANSWER.version().equals(answer.path("schema_version").asText())
            || !"option_keys".equals(answer.path("answer_type").asText())
            || !"single".equals(answer.path("selection_mode").asText())) {
            issue(issues, "QUESTION_CHOICE_ANSWER_INVALID", "answer", "选择题答案结构必须为option_keys单选");
            return;
        }
        JsonNode value = answer.path("value");
        if (!value.isArray() || value.size() != 1 || !value.get(0).isTextual()) {
            issue(issues, "QUESTION_CHOICE_ANSWER_INVALID", "answer.value", "选择题必须保留一个数组形式的标准答案");
            return;
        }
        if (!labels.contains(value.get(0).asText())) {
            issue(issues, "QUESTION_CHOICE_ANSWER_INVALID", "answer.value[0]", "标准答案必须命中当前选项标签");
        }
    }

    private void validateSubjective(List<QuestionRows.Option> options, String answerJson,
                                   List<QuestionErrorVo.BlockingIssueVo> issues) {
        if (!options.isEmpty()) {
            issue(issues, "QUESTION_SUBJECTIVE_OPTIONS_INVALID", "options", "案例题和论文题不得包含选项");
        }
        JsonNode answer = answer(answerJson, issues, "QUESTION_REFERENCE_ANSWER_REQUIRED", "answer");
        if (answer != null && (!SharedJsonSchema.QUESTION_ANSWER.version().equals(answer.path("schema_version").asText())
            || !"reference_text".equals(answer.path("answer_type").asText())
            || !answer.path("value").isTextual() || answer.path("value").asText().isBlank())) {
            issue(issues, "QUESTION_REFERENCE_ANSWER_REQUIRED", "answer.value", "案例题和论文题必须提供非空参考答案");
        }
    }

    private Long validateQuestionKnowledge(QuestionRows.Revision revision,
                                            List<QuestionRows.Knowledge> bindings,
                                            Map<Long, QuestionRows.Knowledge> metadata,
                                            List<QuestionErrorVo.BlockingIssueVo> issues) {
        if (bindings.isEmpty()) {
            return null;
        }
        long primaryCount = bindings.stream().filter(binding -> "primary".equals(binding.getRelationRole())).count();
        if (primaryCount != 1) {
            issue(issues, "QUESTION_PRIMARY_KNOWLEDGE_INVALID", "knowledgeBindings", "题目必须且只能有一个主知识点");
        }
        Set<Long> ids = new HashSet<>();
        Set<Long> syllabuses = new HashSet<>();
        for (int index = 0; index < bindings.size(); index++) {
            QuestionRows.Knowledge binding = bindings.get(index);
            String path = "knowledgeBindings[" + index + "]";
            if (!KNOWLEDGE_ROLES.contains(binding.getRelationRole())) {
                issue(issues, "QUESTION_KNOWLEDGE_BINDING_INVALID", path + ".relationRole", "知识点关系必须为primary或secondary");
            }
            if (!Objects.equals(binding.getSortOrder(), index)) {
                issue(issues, "QUESTION_KNOWLEDGE_BINDING_INVALID", path + ".sortOrder", "知识点顺序必须从0连续");
            }
            Long knowledgePointId = binding.getKnowledgePointId();
            if (knowledgePointId == null || !ids.add(knowledgePointId)) {
                issue(issues, "QUESTION_KNOWLEDGE_BINDING_INVALID", path + ".knowledgePointId", "题目知识点不得重复");
                continue;
            }
            QuestionRows.Knowledge active = metadata.get(knowledgePointId);
            if (active == null) {
                issue(issues, "QUESTION_KNOWLEDGE_NOT_FOUND", path + ".knowledgePointId", "题目知识点不存在或已删除");
                continue;
            }
            if (!Boolean.TRUE.equals(active.getLeaf())) {
                issue(issues, "QUESTION_KNOWLEDGE_NOT_LEAF", path + ".knowledgePointId", "题目知识点必须是最末级知识点");
            }
            if (!Objects.equals(active.getExamSubjectId(), revision.getExamSubjectId())) {
                issue(issues, "QUESTION_KNOWLEDGE_SCOPE_MISMATCH", path + ".knowledgePointId", "题目知识点不属于当前考试科目");
            }
            syllabuses.add(active.getSyllabusVersionId());
        }
        if (syllabuses.size() != 1) {
            issue(issues, "QUESTION_KNOWLEDGE_SCOPE_MISMATCH", "knowledgeBindings", "题目知识点必须属于同一考纲");
            return null;
        }
        return syllabuses.iterator().next();
    }

    private JsonNode answer(String answerJson, List<QuestionErrorVo.BlockingIssueVo> issues,
                            String code, String fieldPath) {
        if (answerJson == null || answerJson.isBlank()) {
            issue(issues, code, fieldPath, "答案结构不能为空");
            return null;
        }
        try {
            JsonNode answer = jsonMapper.readTree(answerJson);
            if (answer == null || !answer.isObject()) {
                issue(issues, code, fieldPath, "答案结构必须为对象");
                return null;
            }
            return answer;
        } catch (Exception exception) {
            issue(issues, code, fieldPath, "答案结构无效");
            return null;
        }
    }

    private static void issue(List<QuestionErrorVo.BlockingIssueVo> issues, String code, String fieldPath, String message) {
        issues.add(new QuestionErrorVo.BlockingIssueVo(code, fieldPath, message));
    }
}
