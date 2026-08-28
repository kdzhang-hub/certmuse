package org.dromara.certmuse.catalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Enables the persistent import-job dispatcher.
 */
@Configuration
@EnableScheduling
public class ImportSchedulingConfiguration {

    /**
     * Creates a dedicated mapper for strict JSONL parsing without changing the global mapper.
     *
     * @return mapper with duplicate-field detection enabled
     */
    @Bean("importStrictJsonMapper")
    public JsonMapper importStrictJsonMapper() {
        return JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();
    }
}
