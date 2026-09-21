package org.dromara.common.push.ticket;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.dromara.common.push.metrics.SseMetrics;
import org.dromara.common.push.properties.MessageProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTicketServiceTest {

    @Test
    void issuedTicketCanOnlyBeConsumedOnce() {
        InMemoryTicketStore store = new InMemoryTicketStore();
        MessageProperties properties = new MessageProperties();
        properties.setTicketTtl(Duration.ofSeconds(60));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MessageTicketService service = new MessageTicketService(store, properties, new SseMetrics(registry));

        MessageTicket issued = service.issue(42L, "admin-client");

        assertThat(issued.ticket()).isNotBlank();
        assertThat(issued.connectionId()).isNotBlank();
        assertThat(issued.expiresInSeconds()).isEqualTo(60);
        assertThat(service.consume(issued.ticket())).contains(new MessageTicketPrincipal(
            42L, "admin-client", issued.connectionId()
        ));
        assertThat(service.consume(issued.ticket())).isEmpty();
        assertThat(registry.counter("certmuse.sse.ticket.issued").count()).isEqualTo(1);
        assertThat(registry.counter("certmuse.sse.ticket.rejected").count()).isEqualTo(1);
    }

    @Test
    void rejectsTicketWithIncompleteStoredIdentity() {
        InMemoryTicketStore store = new InMemoryTicketStore();
        MessageProperties properties = new MessageProperties();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MessageTicketService service = new MessageTicketService(store, properties, new SseMetrics(registry));
        store.put("broken", new MessageTicketPrincipal(42L, "", "connection-1"), Duration.ofSeconds(60));

        assertThat(service.consume("broken")).isEmpty();
        assertThat(registry.counter("certmuse.sse.ticket.rejected").count()).isEqualTo(1);
    }

    private static final class InMemoryTicketStore implements MessageTicketStore {
        private final Map<String, MessageTicketPrincipal> tickets = new HashMap<>();

        @Override
        public void put(String ticket, MessageTicketPrincipal principal, Duration ttl) {
            tickets.put(ticket, principal);
        }

        @Override
        public MessageTicketPrincipal take(String ticket) {
            return tickets.remove(ticket);
        }
    }
}
