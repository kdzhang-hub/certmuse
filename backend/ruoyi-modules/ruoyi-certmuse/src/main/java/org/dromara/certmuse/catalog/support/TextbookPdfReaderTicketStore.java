package org.dromara.certmuse.catalog.support;

import cn.hutool.crypto.digest.DigestUtil;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Redis-backed, reusable ticket store for browser PDF range requests. */
@Component
@RequiredArgsConstructor
public class TextbookPdfReaderTicketStore {
    private static final String KEY_PREFIX = "certmuse:textbook-pdf:reader:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final RedissonClient redissonClient;
    private final JsonMapper jsonMapper;

    public String issue(TextbookPdfReaderTicket ticket, Duration ttl) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        bucket(value).set(jsonMapper.writeValueAsString(ticket), ttl);
        return value;
    }

    public Optional<TextbookPdfReaderTicket> find(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String json = bucket(value).get();
        return json == null ? Optional.empty() : Optional.of(jsonMapper.readValue(json, TextbookPdfReaderTicket.class));
    }

    private RBucket<String> bucket(String value) {
        return redissonClient.getBucket(KEY_PREFIX + DigestUtil.sha256Hex(value), StringCodec.INSTANCE);
    }
}
