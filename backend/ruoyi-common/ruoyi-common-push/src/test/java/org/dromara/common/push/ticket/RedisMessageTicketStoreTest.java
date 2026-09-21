package org.dromara.common.push.ticket;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisMessageTicketStoreTest {

    @Test
    void roundTripsPrincipalThroughStableJsonStringCodec() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        String key = "certmuse:message:sse:ticket:one-time-ticket";
        when(redissonClient.<String>getBucket(key, StringCodec.INSTANCE)).thenReturn(bucket);
        when(bucket.getAndDelete()).thenReturn(
            "{\"userId\":42,\"clientId\":\"admin-client\",\"connectionId\":\"connection-1\"}"
        );
        RedisMessageTicketStore store = new RedisMessageTicketStore(redissonClient, JsonMapper.builder().build());
        MessageTicketPrincipal principal = new MessageTicketPrincipal(42L, "admin-client", "connection-1");

        store.put("one-time-ticket", principal, Duration.ofSeconds(60));
        MessageTicketPrincipal consumed = store.take("one-time-ticket");

        verify(bucket).set(
            "{\"userId\":42,\"clientId\":\"admin-client\",\"connectionId\":\"connection-1\"}",
            Duration.ofSeconds(60)
        );
        assertThat(consumed).isEqualTo(principal);
    }
}
