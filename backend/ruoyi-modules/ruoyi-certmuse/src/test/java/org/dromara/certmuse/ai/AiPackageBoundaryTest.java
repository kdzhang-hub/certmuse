package org.dromara.certmuse.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.dromara.certmuse.ai.client.AiModelClient;
import org.dromara.certmuse.ai.client.OpenAiCompatibleModelClient;
import org.dromara.certmuse.ai.config.CertMuseAiProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Tag("dev")
class AiPackageBoundaryTest {
    private static final List<String> FORBIDDEN_DOMAINS = List.of(
        ".assessment.", ".catalog.", ".learning.", ".profile.", ".question.");

    @Test
    void reusableAiPackageDoesNotDependOnBusinessDomains() throws IOException {
        Path sourceRoot = find("ruoyi-modules/ruoyi-certmuse/src/main/java/org/dromara/certmuse/ai");
        try (var files = Files.walk(sourceRoot)) {
            List<String> sources = files.filter(path -> path.toString().endsWith(".java"))
                .map(this::read).toList();
            assertThat(sources).isNotEmpty();
            FORBIDDEN_DOMAINS.forEach(domain -> assertThat(sources).allMatch(source -> !source.contains(domain)));
            assertThat(sources).allMatch(source -> !source.contains("SseEmitter"));
        }
    }

    @Test
    void modelClientAndConfigurationRemainSpringDiscoverable() {
        assertThat(AiModelClient.class.isAssignableFrom(OpenAiCompatibleModelClient.class)).isTrue();
        assertThat(OpenAiCompatibleModelClient.class).hasAnnotation(Component.class);
        ConfigurationProperties properties = CertMuseAiProperties.class.getAnnotation(ConfigurationProperties.class);
        assertThat(properties.prefix()).isEqualTo("certmuse.ai");
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Path find(String relative) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.exists(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("file not found: " + relative);
    }
}
