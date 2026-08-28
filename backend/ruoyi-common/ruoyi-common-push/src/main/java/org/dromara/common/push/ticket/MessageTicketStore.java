package org.dromara.common.push.ticket;

import java.time.Duration;

/**
 * Atomic one-time ticket storage seam.
 */
public interface MessageTicketStore {

    void put(String ticket, MessageTicketPrincipal principal, Duration ttl);

    MessageTicketPrincipal take(String ticket);
}
