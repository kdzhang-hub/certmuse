package org.dromara.certmuse.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.dromara.certmuse.ai.config.CertMuseAiProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@Tag("dev")
class OpenAiCompatibleModelClientTest {
    @Test
    void usesConfiguredChatCompletionsPath() {
        CertMuseAiProperties properties = new CertMuseAiProperties();
        properties.setBaseUrl("https://ark.cn-beijing.volces.com/api/v3/");
        properties.setChatCompletionsPath("chat/completions");

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            OpenAiCompatibleModelClient client = new OpenAiCompatibleModelClient(
                properties, HttpClient.newHttpClient(), executor, new ObjectMapper());

            assertThat(client.endpoint()).hasToString("https://ark.cn-beijing.volces.com/api/v3/chat/completions");
        }
    }

    @Test
    void fallsBackToOpenAiDefaultPathWhenPathIsBlank() {
        CertMuseAiProperties properties = new CertMuseAiProperties();
        properties.setBaseUrl("https://example.test/");
        properties.setChatCompletionsPath(" ");

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            OpenAiCompatibleModelClient client = new OpenAiCompatibleModelClient(
                properties, HttpClient.newHttpClient(), executor, new ObjectMapper());

            assertThat(client.endpoint()).hasToString("https://example.test/v1/chat/completions");
        }
    }
}
