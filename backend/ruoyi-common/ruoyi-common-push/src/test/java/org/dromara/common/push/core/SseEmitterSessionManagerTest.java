package org.dromara.common.push.core;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.dromara.common.push.metrics.SseMetrics;
import org.dromara.common.push.properties.MessageProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SseEmitterSessionManagerTest {

    private final MessageProperties properties = new MessageProperties();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final SseEmitterSessionManager manager = new SseEmitterSessionManager(
        mock(ScheduledExecutorService.class), properties, new SseMetrics(registry)
    );

    @AfterEach
    void closeConnections() {
        manager.closeAll();
    }

    @Test
    void keepsAtMostConfiguredConnectionsForOneUser() {
        properties.setMaxConnectionsPerUser(2);

        manager.connect(42L, "one");
        manager.connect(42L, "two");
        manager.connect(42L, "three");

        assertThat(manager.currentConnectionCount(42L)).isEqualTo(2);
        assertThat(registry.get("certmuse.sse.connections.current").gauge().value()).isEqualTo(2);
        assertThat(registry.counter("certmuse.sse.connections.opened").count()).isEqualTo(3);
        assertThat(registry.counter("certmuse.sse.connections.closed").count()).isEqualTo(1);
    }
}
