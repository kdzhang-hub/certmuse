package org.dromara.common.push.ticket;

/**
 * Issued one-time SSE connection ticket.
 */
public record MessageTicket(String ticket, String connectionId, long expiresInSeconds) {
}
