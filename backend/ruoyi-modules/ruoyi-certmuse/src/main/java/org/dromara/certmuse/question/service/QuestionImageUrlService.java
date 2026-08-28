package org.dromara.certmuse.question.service;

import org.dromara.common.oss.factory.OssFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class QuestionImageUrlService {
    private static final Duration URL_TTL = Duration.ofMinutes(5);

    public String accessUrl(String sourceUrl, String storagePath) {
        if (storagePath != null && !storagePath.isBlank()) {
            return OssFactory.instance().presignGetUrl(storagePath, URL_TTL);
        }
        return sourceUrl;
    }
}
