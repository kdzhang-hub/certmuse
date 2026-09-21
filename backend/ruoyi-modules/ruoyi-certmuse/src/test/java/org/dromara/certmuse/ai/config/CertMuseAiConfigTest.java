package org.dromara.certmuse.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Tag("dev")
class CertMuseAiConfigTest {
    @Test
    void configurationCreatesBoundedSharedModelResources() {
        CertMuseAiConfig configuration = new CertMuseAiConfig();
        CertMuseAiProperties properties = new CertMuseAiProperties();
        ExecutorService executor = configuration.practiceAiExecutor();
        ScheduledExecutorService scheduler = configuration.practiceAiScheduler();
        try {
            assertThat(configuration.getClass()).hasAnnotation(Configuration.class);
            assertThat(CertMuseAiConfig.class.getDeclaredMethods())
                .filteredOn(method -> method.isAnnotationPresent(Bean.class)).hasSize(3);
            assertThat(executor).isInstanceOf(ThreadPoolExecutor.class);
            assertThat(((ThreadPoolExecutor) executor).getQueue().remainingCapacity()).isEqualTo(64);
            HttpClient client = configuration.practiceAiHttpClient(properties, executor);
            assertThat(client.connectTimeout()).contains(properties.getConnectTimeout());
            assertThat(client.executor()).contains(executor);
            assertThat(scheduler).isNotNull();
        } finally {
            executor.shutdownNow();
            scheduler.shutdownNow();
        }
    }
}
