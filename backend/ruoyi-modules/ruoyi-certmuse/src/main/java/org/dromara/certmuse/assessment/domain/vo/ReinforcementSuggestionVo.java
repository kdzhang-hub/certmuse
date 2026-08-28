package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Source-question reinforcement recommendation. */
public record ReinforcementSuggestionVo(String roundId, String status, List<KnowledgePointVo> knowledgePoints,
                                        String recommendationReason, String reasonSource, int estimatedCount,
                                        String reinforcementSessionId, String answerPath,
                                        List<String> availableActions) {
    public record KnowledgePointVo(String id, String name) {}
}
