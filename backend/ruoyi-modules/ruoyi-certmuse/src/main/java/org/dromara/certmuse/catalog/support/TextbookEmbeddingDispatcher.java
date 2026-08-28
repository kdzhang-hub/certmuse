package org.dromara.certmuse.catalog.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.ai.client.EmbeddingClient;
import org.dromara.certmuse.ai.client.EmbeddingException;
import org.dromara.certmuse.ai.config.CertMuseAiProperties;
import org.dromara.certmuse.catalog.domain.TextbookEmbeddingTaskRow;
import org.dromara.certmuse.catalog.mapper.TextbookEvidenceMapper;
import org.dromara.certmuse.catalog.service.impl.TextbookEvidenceServiceImpl;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Executes durable textbook embedding work outside content transactions. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextbookEmbeddingDispatcher {
    private static final int MAX_ATTEMPTS = 3;
    private final TextbookEvidenceMapper mapper;
    private final EmbeddingClient embeddingClient;
    private final CertMuseAiProperties properties;

    @Scheduled(fixedDelayString = "${certmuse.ai.embedding-dispatch-delay-ms:1000}")
    public void dispatch() {
        if (!properties.isRagEnabled() || blank(properties.getEmbeddingModel())) return;
        TextbookEmbeddingTaskRow task = mapper.claimEmbeddingTask(
            properties.getEmbeddingModel(), properties.getEmbeddingDimensions());
        if (task == null) return;
        try {
            String input = (task.getHeading() == null ? "" : task.getHeading() + "\n") + task.getContent();
            String vector = TextbookEvidenceServiceImpl.vector(embeddingClient.embed(input));
            if (mapper.completeEmbeddingTask(task.getChunkId(), task.getContentHash(), properties.getEmbeddingModel(),
                properties.getEmbeddingDimensions(), vector) != 1) {
                mapper.retryEmbeddingTask(task.getChunkId(), "AI_EMBEDDING_STALE", MAX_ATTEMPTS);
            }
        } catch (EmbeddingException exception) {
            log.warn("Textbook embedding failed, chunkId={}, errorCode={}", task.getChunkId(), exception.getErrorCode());
            mapper.retryEmbeddingTask(task.getChunkId(), exception.getErrorCode(), MAX_ATTEMPTS);
        } catch (Exception exception) {
            log.warn("Textbook embedding failed, chunkId={}, errorCode={}", task.getChunkId(), "AI_EMBEDDING_SYSTEM_FAILURE");
            mapper.retryEmbeddingTask(task.getChunkId(), "AI_EMBEDDING_SYSTEM_FAILURE", MAX_ATTEMPTS);
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
