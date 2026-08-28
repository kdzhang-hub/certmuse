package org.dromara.certmuse.ai.domain;

import java.util.List;

/** Immutable input to one bounded Agent execution. */
public record AiAgentRequest(
    AiAgentTaskType taskType,
    String resourceType,
    long resourceId,
    List<Long> knowledgePointIds,
    String retrievalQuery,
    AiModelRequest modelRequest
) {
    public AiAgentRequest {
        knowledgePointIds = knowledgePointIds == null ? List.of() : List.copyOf(knowledgePointIds);
    }
}
