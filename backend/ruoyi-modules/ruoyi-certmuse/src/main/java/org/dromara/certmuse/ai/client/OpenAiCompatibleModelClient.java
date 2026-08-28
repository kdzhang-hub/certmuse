package org.dromara.certmuse.ai.client;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.ai.config.CertMuseAiProperties;
import org.dromara.certmuse.ai.domain.AiModelRequest;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** OpenAI-compatible chat-completions streaming adapter. */
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleModelClient implements AiModelClient, AiStructuredCompletionClient {
    private final CertMuseAiProperties properties;
    private final HttpClient practiceAiHttpClient;
    private final ExecutorService practiceAiExecutor;
    private final ObjectMapper objectMapper;

    @Override
    public AiGenerationHandle stream(AiModelRequest request, AiModelStreamListener listener) {
        AtomicBoolean cancelled = new AtomicBoolean();
        CompletableFuture<Void> future;
        try {
            future = CompletableFuture.runAsync(() -> execute(request, listener, cancelled), practiceAiExecutor);
        } catch (RejectedExecutionException exception) {
            listener.onFailure("AI_PROVIDER_UNAVAILABLE", exception);
            return () -> cancelled.set(true);
        }
        return () -> {
            cancelled.set(true);
            future.cancel(true);
        };
    }

    @Override
    public String complete(AiModelRequest request) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint())
                .timeout(properties.getTotalTimeout())
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(structuredPayload(request), StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = practiceAiHttpClient.send(
                httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiStructuredCompletionException("AI_PROVIDER_UNAVAILABLE",
                    "AI provider returned HTTP " + response.statusCode(), null);
            }
            String content = objectMapper.readTree(response.body()).path("choices").path(0)
                .path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new AiStructuredCompletionException("AI_OUTPUT_INVALID", "AI provider returned an empty completion", null);
            }
            return content;
        } catch (AiStructuredCompletionException exception) {
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new AiStructuredCompletionException("AI_PROVIDER_TIMEOUT", "AI provider timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiStructuredCompletionException("AI_GENERATION_INTERRUPTED", "AI completion interrupted", exception);
        } catch (Exception exception) {
            throw new AiStructuredCompletionException("AI_PROVIDER_UNAVAILABLE", "AI provider completion failed", exception);
        }
    }

    private void execute(AiModelRequest request, AiModelStreamListener listener, AtomicBoolean cancelled) {
        AtomicBoolean completed = new AtomicBoolean();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint())
                .timeout(properties.getTotalTimeout())
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(payload(request), StandardCharsets.UTF_8))
                .build();
            HttpResponse<InputStream> response = practiceAiHttpClient.send(
                httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                listener.onFailure(response.statusCode() == 429 ? "AI_PROVIDER_UNAVAILABLE" : "AI_PROVIDER_UNAVAILABLE",
                    new IllegalStateException("AI provider returned HTTP " + response.statusCode()));
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while (!cancelled.get() && (line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        if (completed.compareAndSet(false, true)) listener.onCompleted("STOP");
                        return;
                    }
                    if (consume(data, listener)) {
                        completed.set(true);
                        return;
                    }
                }
            }
        } catch (java.net.http.HttpTimeoutException exception) {
            listener.onFailure("AI_PROVIDER_TIMEOUT", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (!cancelled.get()) listener.onFailure("AI_GENERATION_INTERRUPTED", exception);
        } catch (Exception exception) {
            if (!cancelled.get()) listener.onFailure("AI_PROVIDER_UNAVAILABLE", exception);
        }
    }

    private boolean consume(String data, AiModelStreamListener listener) throws Exception {
        JsonNode root = objectMapper.readTree(data);
        JsonNode choice = root.path("choices").path(0);
        String delta = choice.path("delta").path("content").asText("");
        if (!delta.isEmpty()) listener.onDelta(delta);
        String finish = choice.path("finish_reason").asText("");
        if (!finish.isEmpty()) {
            listener.onCompleted(switch (finish) {
                case "length" -> "LENGTH";
                case "content_filter" -> "CONTENT_FILTER";
                default -> "STOP";
            });
            return true;
        }
        return false;
    }

    private String payload(AiModelRequest request) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (int index = 0; index < request.messages().size(); index++) {
            AiModelRequest.Message message = request.messages().get(index);
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("role", message.role());
            if (index == request.messages().size() - 1 && !request.images().isEmpty()) {
                List<Map<String, Object>> parts = new ArrayList<>();
                parts.add(Map.of("type", "text", "text", message.content()));
                request.images().forEach(image -> parts.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", "data:" + image.mediaType() + ";base64," + image.base64Data()))));
                value.put("content", parts);
            } else {
                value.put("content", message.content());
            }
            messages.add(value);
        }
        return objectMapper.writeValueAsString(Map.of(
            "model", request.model(),
            "stream", true,
            "stream_options", Map.of("include_usage", true),
            "messages", messages));
    }

    private String structuredPayload(AiModelRequest request) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", request.model());
        payload.put("stream", false);
        payload.put("temperature", 0);
        payload.put("response_format", Map.of("type", "json_object"));
        payload.put("messages", messages(request));
        return objectMapper.writeValueAsString(payload);
    }

    private List<Map<String, Object>> messages(AiModelRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (int index = 0; index < request.messages().size(); index++) {
            AiModelRequest.Message message = request.messages().get(index);
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("role", message.role());
            if (index == request.messages().size() - 1 && !request.images().isEmpty()) {
                List<Map<String, Object>> parts = new ArrayList<>();
                parts.add(Map.of("type", "text", "text", message.content()));
                request.images().forEach(image -> parts.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", "data:" + image.mediaType() + ";base64," + image.base64Data()))));
                value.put("content", parts);
            } else {
                value.put("content", message.content());
            }
            messages.add(value);
        }
        return messages;
    }

    URI endpoint() {
        String base = properties.getBaseUrl().replaceAll("/+$", "");
        String path = properties.getChatCompletionsPath();
        if (path == null || path.isBlank()) {
            path = "/v1/chat/completions";
        } else if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return URI.create(base + path);
    }
}
