package org.dromara.common.push.ticket;

import lombok.RequiredArgsConstructor;
import org.dromara.common.push.properties.MessageProperties;
import org.dromara.common.push.metrics.SseMetrics;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and atomically consumes short-lived SSE tickets.
 */
@RequiredArgsConstructor
public class MessageTicketService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final MessageTicketStore store;
    private final MessageProperties properties;
    private final SseMetrics metrics;

    public MessageTicket issue(Long userId, String clientId) {
        if (userId == null || clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("SSE ticket identity is incomplete");
        }
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        String connectionId = UUID.randomUUID().toString();
        store.put(ticket, new MessageTicketPrincipal(userId, clientId, connectionId), properties.getTicketTtl());
        metrics.ticketIssued();
        return new MessageTicket(ticket, connectionId, properties.getTicketTtl().toSeconds());
    }

    public Optional<MessageTicketPrincipal> consume(String ticket) {
        Optional<MessageTicketPrincipal> principal = Optional.ofNullable(store.take(ticket))
            .filter(MessageTicketService::isValidPrincipal);
        if (principal.isEmpty()) {
            metrics.ticketRejected();
        }
        return principal;
    }

    private static boolean isValidPrincipal(MessageTicketPrincipal principal) {
        return principal.userId() != null
            && principal.clientId() != null && !principal.clientId().isBlank()
            && principal.connectionId() != null && !principal.connectionId().isBlank();
    }
}
