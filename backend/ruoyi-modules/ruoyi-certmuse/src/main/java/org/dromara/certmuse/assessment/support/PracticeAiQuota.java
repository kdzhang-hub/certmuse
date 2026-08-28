package org.dromara.certmuse.assessment.support;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import org.dromara.certmuse.ai.config.CertMuseAiProperties;
import org.dromara.common.redis.utils.RedisUtils;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.springframework.stereotype.Component;

/** Redis-backed user generation concurrency and quota gates. */
@Component
public class PracticeAiQuota {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final CertMuseAiProperties properties;

    public PracticeAiQuota(CertMuseAiProperties properties) {
        this.properties = properties;
    }

    public boolean acquireSlot(long userId, long assistantMessageId) {
        RBucket<Long> bucket = RedisUtils.getClient().getBucket(slotKey(userId));
        return bucket.trySet(assistantMessageId, properties.getTotalTimeout().plusSeconds(30).toMillis(),
            java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void releaseSlot(long userId, long assistantMessageId) {
        RBucket<Long> bucket = RedisUtils.getClient().getBucket(slotKey(userId));
        Long current = bucket.get();
        if (current != null && current == assistantMessageId) bucket.delete();
    }

    public boolean acquireMinute(long userId) {
        RRateLimiter limiter = RedisUtils.getClient().getRateLimiter("certmuse:ai:rate:minute:" + userId);
        limiter.trySetRate(RateType.OVERALL, 10, Duration.ofSeconds(60), Duration.ZERO);
        return limiter.tryAcquire();
    }

    public boolean acquireDay(long userId) {
        String key = "certmuse:ai:rate:day:" + LocalDate.now(SHANGHAI) + ":" + userId;
        RAtomicLong counter = RedisUtils.getClient().getAtomicLong(key);
        long count = counter.incrementAndGet();
        if (count == 1) counter.expire(Duration.ofDays(2));
        return count <= 100;
    }

    public int secondsUntilNextDay() {
        return (int) Duration.between(java.time.ZonedDateTime.now(SHANGHAI),
            LocalDate.now(SHANGHAI).plusDays(1).atStartOfDay(SHANGHAI)).toSeconds();
    }

    private String slotKey(long userId) {
        return "certmuse:ai:generation:user:" + userId;
    }
}
