package org.dromara.certmuse.ai.domain;

import java.util.List;

/** Prepared model request, run identity, and citations for one Agent execution. */
public record AiAgentExecution(long runId, AiModelRequest modelRequest, List<AiCitation> citations) {
    public AiAgentExecution {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
