package org.dromara.certmuse.ai.client;

import org.dromara.certmuse.ai.domain.AiModelRequest;

/** Provider adapter for a single JSON-only model completion. */
public interface AiStructuredCompletionClient {

    /**
     * Generates one structured completion for trusted server-side context.
     *
     * @param request provider-neutral request
     * @return assistant message content, expected to be a JSON document
     */
    String complete(AiModelRequest request);
}
