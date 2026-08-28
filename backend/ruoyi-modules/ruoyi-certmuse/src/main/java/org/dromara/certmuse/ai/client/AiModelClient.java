package org.dromara.certmuse.ai.client;

import org.dromara.certmuse.ai.domain.AiModelRequest;

/** Provider adapter for a cancellable streaming chat completion. */
public interface AiModelClient {
    AiGenerationHandle stream(AiModelRequest request, AiModelStreamListener listener);
}
