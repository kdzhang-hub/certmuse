package org.dromara.certmuse.assessment.support;

import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.ai.client.AiStructuredCompletionException;
import org.dromara.certmuse.ai.config.CertMuseAiProperties;
import org.dromara.certmuse.ai.domain.AiAgentExecution;
import org.dromara.certmuse.ai.domain.AiAgentRequest;
import org.dromara.certmuse.ai.domain.AiAgentTaskType;
import org.dromara.certmuse.ai.domain.AiModelRequest;
import org.dromara.certmuse.agent.service.AiAgentExecutor;
import org.dromara.certmuse.assessment.domain.AiGradingTaskRow;
import org.dromara.certmuse.assessment.domain.QuestionAiRubricRow;
import org.dromara.certmuse.assessment.mapper.AiGradingMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Executes durable structured AI grading tasks outside learner-session transactions. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubjectiveGradingTaskDispatcher {
    private final AiGradingMapper mapper;
    private final AiAgentExecutor agentExecutor;
    private final CertMuseAiProperties properties;
    private final JsonMapper jsonMapper;

    @Scheduled(fixedDelayString = "${certmuse.ai.grading-dispatch-delay-ms:1000}")
    public void dispatch() {
        int reclaimed = mapper.reclaimExpiredTasks(properties.getGradingLeaseTimeout().toSeconds());
        if (reclaimed > 0) {
            log.warn("Reclaimed expired subjective grading tasks, count={}", reclaimed);
        }
        AiGradingTaskRow task = mapper.claimTask();
        if (task == null) {
            return;
        }
        try {
            if (!properties.isEnabled() || blank(properties.getModel())) {
                finishFailure(task, "AI_GRADING_DISABLED");
                return;
            }
            JsonNode taskPayload = payload(task);
            AiAgentTaskType taskType = "RUBRIC_GENERATION".equals(task.getTaskType())
                ? AiAgentTaskType.RUBRIC_GENERATION : AiAgentTaskType.ANSWER_GRADING;
            List<Long> knowledgeIds = new ArrayList<>();
            taskPayload.path("knowledgePointIds").forEach(value -> {
                long id = value.asLong(0);
                if (id > 0) knowledgeIds.add(id);
            });
            AiAgentExecution execution = agentExecutor.prepare(new AiAgentRequest(taskType,
                "cm_ai_grading_task", task.getId(), knowledgeIds,
                taskPayload.path("retrievalQuery").asText(""), request(task)));
            String output = agentExecutor.complete(execution);
            JsonNode result = jsonMapper.readTree(output);
            if (!result.isObject()) {
                throw new AiStructuredCompletionException("AI_OUTPUT_INVALID", "AI output must be a JSON object", null);
            }
            if ("RUBRIC_GENERATION".equals(task.getTaskType())) {
                persistRubric(task, payload(task), result);
            } else if ("ANSWER_GRADING".equals(task.getTaskType())) {
                validateAnswerResult(task, result);
            }
            mapper.succeedTask(task.getId(), result.toString());
        } catch (AiStructuredCompletionException exception) {
            finishFailure(task, exception.getErrorCode());
        } catch (Exception exception) {
            log.warn("Subjective grading task failed, taskId={}, type={}, errorCode={}",
                task.getId(), task.getTaskType(), "AI_GRADING_SYSTEM_FAILURE");
            finishFailure(task, "AI_GRADING_SYSTEM_FAILURE");
        }
    }

    private void validateAnswerResult(AiGradingTaskRow task, JsonNode result) {
        QuestionAiRubricRow rubric = mapper.selectRubric(task.getQuestionRevisionId());
        JsonNode rubricData;
        try {
            rubricData = rubric == null ? null : jsonMapper.readTree(rubric.getRubricData());
        } catch (Exception exception) {
            throw new AiStructuredCompletionException("AI_OUTPUT_INVALID", "Frozen rubric is invalid", exception);
        }
        if (SubjectiveAnswerValidator.gradingResult(rubricData, result) == null) {
            throw new AiStructuredCompletionException(SubjectiveAnswerValidator.gradingErrorCode(rubricData, result),
                "AI grading result does not match rubric", null);
        }
    }

    private void finishFailure(AiGradingTaskRow task, String errorCode) {
        if (mapper.retryTask(task.getId(), errorCode) == 0) {
            mapper.failTask(task.getId(), errorCode);
        }
    }

    private AiModelRequest request(AiGradingTaskRow task) throws Exception {
        JsonNode payload = payload(task);
        JsonNode messagesNode = payload.path("messages");
        if (!messagesNode.isArray() || messagesNode.isEmpty()) {
            throw new AiStructuredCompletionException("AI_TASK_PAYLOAD_INVALID", "AI task messages are required", null);
        }
        List<AiModelRequest.Message> messages = new ArrayList<>();
        for (JsonNode message : messagesNode) {
            String role = message.path("role").asText();
            String content = message.path("content").asText();
            if (blank(role) || blank(content)) {
                throw new AiStructuredCompletionException("AI_TASK_PAYLOAD_INVALID", "AI task message is invalid", null);
            }
            messages.add(new AiModelRequest.Message(role, content));
        }
        String model = payload.path("model").asText(properties.getModel());
        return new AiModelRequest(model, List.copyOf(messages), List.of());
    }

    private void persistRubric(AiGradingTaskRow task, JsonNode payload, JsonNode rubric) {
        long revisionId = payload.path("questionRevisionId").asLong(0);
        String contextHash = payload.path("contextHash").asText();
        String promptVersion = payload.path("promptVersion").asText();
        if (revisionId < 1 || blank(contextHash) || blank(promptVersion)) {
            throw new AiStructuredCompletionException("AI_TASK_PAYLOAD_INVALID", "Rubric task context is invalid", null);
        }
        JsonNode items = rubric.path("items");
        if (!items.isArray() || items.isEmpty()) {
            throw new AiStructuredCompletionException("AI_OUTPUT_INVALID", "Rubric items are required", null);
        }
        BigDecimal weight = BigDecimal.ZERO;
        for (JsonNode item : items) {
            if (blank(item.path("code").asText()) || blank(item.path("description").asText())) {
                throw new AiStructuredCompletionException("AI_OUTPUT_INVALID", "Rubric item is invalid", null);
            }
            try {
                BigDecimal value = new BigDecimal(item.path("weight").asText());
                if (value.signum() <= 0) throw new NumberFormatException();
                weight = weight.add(value);
            } catch (NumberFormatException exception) {
                throw new AiStructuredCompletionException("AI_OUTPUT_INVALID", "Rubric weight is invalid", exception);
            }
        }
        if (weight.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.0001")) > 0) {
            throw new AiStructuredCompletionException("AI_OUTPUT_INVALID", "Rubric weights must sum to one", null);
        }
        if (mapper.selectRubric(revisionId) == null) {
            mapper.insertRubric(cn.hutool.core.util.IdUtil.getSnowflakeNextId(), revisionId, contextHash,
                rubric.toString(), properties.getModel(), promptVersion);
        }
    }

    private JsonNode payload(AiGradingTaskRow task) throws Exception {
        return jsonMapper.readTree(task.getPayload());
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
