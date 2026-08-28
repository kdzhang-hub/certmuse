package org.dromara.certmuse.ai.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.ai.config.CertMuseAiProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** OpenAI-compatible embeddings adapter. */
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {
    private final CertMuseAiProperties properties;
    private final HttpClient practiceAiHttpClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<Double> embed(String input) {
        if (input == null || input.isBlank() || blank(properties.getEmbeddingModel())) {
            throw new EmbeddingException("AI_EMBEDDING_CONFIG_INVALID", "Embedding input or model is missing", null);
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", properties.getEmbeddingModel());
            payload.put("input", input);
            payload.put("dimensions", properties.getEmbeddingDimensions());
            HttpRequest request = HttpRequest.newBuilder(endpoint())
                .timeout(properties.getTotalTimeout())
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = practiceAiHttpClient.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new EmbeddingException("AI_EMBEDDING_PROVIDER_UNAVAILABLE",
                    "Embedding provider returned HTTP " + response.statusCode(), null);
            }
            JsonNode values = objectMapper.readTree(response.body()).path("data").path(0).path("embedding");
            if (!values.isArray() || values.size() != properties.getEmbeddingDimensions()) {
                throw new EmbeddingException("AI_EMBEDDING_OUTPUT_INVALID", "Embedding dimensions do not match", null);
            }
            List<Double> result = new ArrayList<>(values.size());
            values.forEach(value -> result.add(value.asDouble()));
            return List.copyOf(result);
        } catch (EmbeddingException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException("AI_EMBEDDING_INTERRUPTED", "Embedding request interrupted", exception);
        } catch (Exception exception) {
            throw new EmbeddingException("AI_EMBEDDING_PROVIDER_UNAVAILABLE", "Embedding request failed", exception);
        }
    }

    private URI endpoint() {
        String base = properties.getBaseUrl().replaceAll("/+$", "");
        String path = properties.getEmbeddingsPath();
        if (blank(path)) path = "/v1/embeddings";
        else if (!path.startsWith("/")) path = "/" + path;
        return URI.create(base + path);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
