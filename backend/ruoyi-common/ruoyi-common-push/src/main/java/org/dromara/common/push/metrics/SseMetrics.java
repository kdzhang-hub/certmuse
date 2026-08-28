package org.dromara.common.push.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Records operational metrics for the SSE transport without exposing message contents or credentials.
 */
public class SseMetrics {

    private final AtomicInteger currentConnections = new AtomicInteger();
    private final Counter connectionsOpened;
    private final Counter connectionsClosed;
    private final Counter ticketsIssued;
    private final Counter ticketsRejected;
    private final Counter deliveriesSucceeded;
    private final Counter deliveriesFailed;
    private final Counter heartbeatFailures;

    public SseMetrics(MeterRegistry registry) {
        Gauge.builder("certmuse.sse.connections.current", currentConnections, AtomicInteger::get)
            .description("Current local SSE connections")
            .register(registry);
        connectionsOpened = registry.counter("certmuse.sse.connections.opened");
        connectionsClosed = registry.counter("certmuse.sse.connections.closed");
        ticketsIssued = registry.counter("certmuse.sse.ticket.issued");
        ticketsRejected = registry.counter("certmuse.sse.ticket.rejected");
        deliveriesSucceeded = registry.counter("certmuse.sse.deliveries.succeeded");
        deliveriesFailed = registry.counter("certmuse.sse.deliveries.failed");
        heartbeatFailures = registry.counter("certmuse.sse.heartbeat.failures");
    }

    public void connectionOpened() {
        currentConnections.incrementAndGet();
        connectionsOpened.increment();
    }

    public void connectionClosed() {
        currentConnections.updateAndGet(value -> Math.max(0, value - 1));
        connectionsClosed.increment();
    }

    public void ticketIssued() {
        ticketsIssued.increment();
    }

    public void ticketRejected() {
        ticketsRejected.increment();
    }

    public void deliverySucceeded() {
        deliveriesSucceeded.increment();
    }

    public void deliveryFailed() {
        deliveriesFailed.increment();
    }

    public void heartbeatFailed() {
        heartbeatFailures.increment();
    }
}
