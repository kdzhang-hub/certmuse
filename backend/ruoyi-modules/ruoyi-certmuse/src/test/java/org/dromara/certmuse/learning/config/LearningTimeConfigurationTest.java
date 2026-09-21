package org.dromara.certmuse.learning.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class LearningTimeConfigurationTest {
    @Test
    void usesShanghaiAsTheAuthoritativeLearningClockZone() {
        assertThat(new LearningTimeConfiguration().learningClock().getZone()).isEqualTo(LearningTimeConfiguration.SHANGHAI);
        assertThat(LearningTimeConfiguration.SHANGHAI.getId()).isEqualTo("Asia/Shanghai");
    }
}
