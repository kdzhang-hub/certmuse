package org.dromara.certmuse.assessment.support;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.assessment.domain.AiGradingTaskRow;
import org.dromara.certmuse.assessment.domain.QuestionAiRubricRow;
import org.dromara.certmuse.assessment.mapper.AiGradingMapper;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Creates idempotent rubric and answer-grading tasks from frozen question snapshots. */
@Component
@RequiredArgsConstructor
public class SubjectiveGradingTasks {
    private final AiGradingMapper mapper;
    private final JsonMapper jsonMapper;

    /** Ensures the revision rubric and attempt grading task exist, returning its durable state. */
    public TaskState ensure(long revisionId, long sessionQuestionId, long attemptId, String presentation,
                            String grading, String knowledge, String answer) {
        QuestionAiRubricRow rubric = mapper.selectRubric(revisionId);
        if (rubric == null) {
            String key = "RUBRIC:" + revisionId;
            AiGradingTaskRow rubricTask = mapper.selectTask(key);
            if (rubricTask == null) {
                JsonNode p = tree(presentation); JsonNode g = tree(grading); JsonNode k = tree(knowledge);
                String context = write(Map.of("question", p.path("stem").asText(),
                    "referenceAnswer", g.path("answer").path("value").asText(), "knowledge", k.path("items")));
                mapper.insertTask(IdUtil.getSnowflakeNextId(), key, "RUBRIC_GENERATION", revisionId, null, null,
                    write(Map.of("questionRevisionId", revisionId, "contextHash", DigestUtil.sha256Hex(context),
                        "promptVersion", "subjective-rubric/1.1", "messages", List.of(
                            Map.of("role", "system", "content", "你是一名公平、严谨的考试评分标准设计专家。"),
                            Map.of("role", "user", "content", "仅返回 JSON，对象中必须包含 items。每个评分项必须包含 code、description 和正数 weight，所有 weight 之和必须为 1。description 必须使用中文。请基于以下可信题目上下文生成稳定的评分量规：" + context)))));
                return new TaskState("RUBRIC_PENDING", null, null, null);
            }
            if ("failed".equals(rubricTask.getStatus())) {
                return new TaskState("FAILED", null, rubricTask.getErrorCode(), null);
            }
            if ("succeeded".equals(rubricTask.getStatus())) {
                return new TaskState("FAILED", null, "AI_RUBRIC_PERSISTENCE_FAILED", null);
            }
            return new TaskState(rubricTask.getStatus().toUpperCase(), null, rubricTask.getErrorCode(), null);
        }
        String key = "ANSWER:" + attemptId;
        AiGradingTaskRow task = mapper.selectTask(key);
        if (task == null) {
            JsonNode p = tree(presentation); JsonNode g = tree(grading);
            String prompt = "仅返回 JSON，必须包含介于 0 和 1 之间的 scoreRate、中文 feedback 和 itemResults。"
                + "itemResults 必须与量规逐项对应，每项包含 code 和 0 到 1 的 scoreRate。"
                + "只可依据以下冻结的评分量规和参考答案评分，不得使用其他标准。题目："
                + p.path("stem").asText() + "。参考答案：" + g.path("answer").path("value").asText()
                + "。评分量规：" + rubric.getRubricData() + "。学生答案：" + answer;
            mapper.insertTask(IdUtil.getSnowflakeNextId(), key, "ANSWER_GRADING", revisionId, sessionQuestionId, attemptId,
                write(Map.of("messages", List.of(Map.of("role", "system", "content", "你是一名严格、客观的考试阅卷教师。所有面向用户的文字必须使用中文。"),
                    Map.of("role", "user", "content", prompt)))));
            return new TaskState("PENDING", null, null, rubric.getRubricData());
        }
        return new TaskState(task.getStatus().toUpperCase(), task.getResult(), task.getErrorCode(), rubric.getRubricData());
    }

    private JsonNode tree(String value) { try { return jsonMapper.readTree(value); } catch (Exception exception) { throw new IllegalStateException("frozen subjective snapshot is invalid", exception); } }
    private String write(Object value) { try { return jsonMapper.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException("subjective task serialization failed", exception); } }

    public record TaskState(String status, String result, String errorCode, String rubric) { }
}
