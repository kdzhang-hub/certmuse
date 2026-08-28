package org.dromara.certmuse.assessment.support;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.agent.service.AiAgentExecutor;
import org.dromara.certmuse.ai.config.CertMuseAiProperties;
import org.dromara.certmuse.ai.domain.AiAgentExecution;
import org.dromara.certmuse.ai.domain.AiAgentRequest;
import org.dromara.certmuse.ai.domain.AiAgentTaskType;
import org.dromara.certmuse.ai.domain.AiModelRequest;
import org.dromara.certmuse.assessment.domain.ReinforcementRoundRow;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Generates an optional reinforcement reason only after the learner requests a round. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReinforcementRecommendationGenerator {
    private final AiAgentExecutor agentExecutor;
    private final CertMuseAiProperties properties;
    private final JsonMapper jsonMapper;

    public Recommendation generate(ReinforcementRoundRow round) {
        String fallback = Boolean.TRUE.equals(round.getSourceCorrect())
            ? "这道题已经掌握得不错，再做几道同知识点题可以帮助你确认理解是否稳定。"
            : "这道题对应的知识点还需要巩固，建议通过同知识点题目及时查漏补缺。";
        if (!properties.isEnabled() || !properties.isAgentEnabled()) {
            return new Recommendation(fallback, "RULE", null);
        }
        try {
            JsonNode knowledge = jsonMapper.readTree(round.getKnowledgeSnapshot());
            List<Long> ids = new java.util.ArrayList<>();
            List<String> names = new java.util.ArrayList<>();
            for (JsonNode item : knowledge.path("items")) {
                ids.add(Long.parseLong(item.path("knowledgePointId").asText()));
                names.add(item.path("knowledgePointName").asText());
            }
            String user = "请根据可信题目事实生成一句不超过80字的学习强化推荐理由，只输出JSON："
                + "{\"recommendationReason\":\"...\"}。不得生成题目、答案或正确选项。知识点=" + names
                + "；本次作答是否正确=" + round.getSourceCorrect() + "；冻结题面=" + round.getSourcePresentationSnapshot();
            AiModelRequest model = new AiModelRequest(properties.getModel(), List.of(
                new AiModelRequest.Message("system", "你是受控学习建议Agent，只能生成推荐理由。"),
                new AiModelRequest.Message("user", user)), List.of());
            AiAgentExecution execution = agentExecutor.prepare(new AiAgentRequest(
                AiAgentTaskType.REINFORCEMENT_RECOMMENDATION, "cm_reinforcement_round", round.getId(), ids,
                String.join(" ", names), model));
            String reason = jsonMapper.readTree(agentExecutor.complete(execution)).path("recommendationReason").asText();
            if (reason.isBlank() || reason.length() > 500) throw new IllegalArgumentException("invalid recommendation");
            return new Recommendation(reason, "AGENT", null);
        } catch (Exception exception) {
            log.warn("Reinforcement recommendation degraded, roundId={}", round.getId());
            return new Recommendation(fallback, "RULE", "REINFORCEMENT_AGENT_FALLBACK");
        }
    }

    public record Recommendation(String reason, String source, String errorCode) {}
}
