package org.dromara.common.push.ticket;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

/**
 * Redis-backed one-time SSE ticket store.
 */
@RequiredArgsConstructor
public class RedisMessageTicketStore implements MessageTicketStore {

    private static final String KEY_PREFIX = "certmuse:message:sse:ticket:";
    private final RedissonClient redissonClient;
    private final JsonMapper jsonMapper;

    @Override
    public void put(String ticket, MessageTicketPrincipal principal, Duration ttl) {
        ticketBucket(ticket).set(jsonMapper.writeValueAsString(principal), ttl);
    }

    @Override
    public MessageTicketPrincipal take(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        String principalJson = ticketBucket(ticket).getAndDelete();
        return principalJson == null ? null : jsonMapper.readValue(principalJson, MessageTicketPrincipal.class);
    }

    /**
     * Ticket values deliberately use a stable string codec instead of the application's polymorphic
     * default Redis codec. Both issue and consume must use the same wire format because GETDEL removes
     * the one-time value before decoding it.
     */
    private RBucket<String> ticketBucket(String ticket) {
        return redissonClient.getBucket(KEY_PREFIX + ticket, StringCodec.INSTANCE);
    }
}
