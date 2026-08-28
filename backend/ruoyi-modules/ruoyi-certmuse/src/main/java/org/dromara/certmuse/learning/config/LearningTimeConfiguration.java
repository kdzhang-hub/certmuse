package org.dromara.certmuse.learning.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Supplies the authoritative time zone used by learner goal validation. */
@Configuration
public class LearningTimeConfiguration {
    public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Bean
    public Clock learningClock() {
        return Clock.system(SHANGHAI);
    }
}
