package org.dromara.common.push.ticket;

import java.io.Serializable;

/**
 * Identity bound to a one-time SSE ticket.
 */
public record MessageTicketPrincipal(Long userId, String clientId, String connectionId) implements Serializable {
}
