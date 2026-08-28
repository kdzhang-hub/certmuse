package org.dromara.certmuse.ai.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/** Runtime settings for the learner question AI tutor. */
@Data
@Component
@ConfigurationProperties(prefix = "certmuse.ai")
public class CertMuseAiProperties {
    private boolean enabled;
    private boolean agentEnabled;
    private boolean ragEnabled;
    private boolean citationEnabled = true;
    private String baseUrl;
    private String apiKey;
    private String model;
    private String chatCompletionsPath = "/v1/chat/completions";
    private String embeddingsPath = "/v1/embeddings";
    private String embeddingModel;
    private int embeddingDimensions = 1536;
    private int retrievalCandidateLimit = 30;
    private int retrievalEvidenceLimit = 6;
    private int retrievalMaxCharacters = 12000;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration firstTokenTimeout = Duration.ofSeconds(20);
    private Duration totalTimeout = Duration.ofSeconds(90);
    private Duration gradingLeaseTimeout = Duration.ofMinutes(2);
    private int maxOutputCharacters = 4000;
    private int maxImageCount = 4;
    private DataSize maxImageBytes = DataSize.ofMegabytes(10);
    private DataSize maxTotalImageBytes = DataSize.ofMegabytes(20);
}
