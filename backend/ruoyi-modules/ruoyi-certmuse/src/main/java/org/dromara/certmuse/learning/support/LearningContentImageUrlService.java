package org.dromara.certmuse.learning.support;

import java.time.Duration;
import org.dromara.common.oss.factory.OssFactory;
import org.springframework.stereotype.Component;

/** Resolves frozen textbook object identifiers into short-lived access URLs. */
@Component
public class LearningContentImageUrlService {
    private static final Duration URL_TTL = Duration.ofMinutes(5);

    public String accessUrl(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("frozen textbook image storage path is missing");
        }
        return OssFactory.instance().presignGetUrl(storagePath, URL_TTL);
    }
}
