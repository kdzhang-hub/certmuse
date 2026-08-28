package org.dromara.certmuse.ai.config;

import java.net.http.HttpClient;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Dedicated bounded resources for model streams and SSE heartbeats. */
@Configuration
public class CertMuseAiConfig {
    @Bean(destroyMethod = "shutdown", name = "practiceAiExecutor")
    ExecutorService practiceAiExecutor() {
        return new ThreadPoolExecutor(16, 16, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(64), Thread.ofPlatform().name("practice-ai-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(destroyMethod = "shutdown", name = "practiceAiScheduler")
    ScheduledExecutorService practiceAiScheduler() {
        return Executors.newScheduledThreadPool(2, Thread.ofPlatform().name("practice-ai-timer-", 0).factory());
    }

    @Bean
    HttpClient practiceAiHttpClient(CertMuseAiProperties properties, ExecutorService practiceAiExecutor) {
        return HttpClient.newBuilder()
            .connectTimeout(properties.getConnectTimeout())
            .executor(practiceAiExecutor)
            .build();
    }
}
