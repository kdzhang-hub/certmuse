package org.dromara.certmuse.catalog.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class ImportJacksonConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withBean("jsonMapper", JsonMapper.class, () -> JsonMapper.builder().build(), definition -> definition.setPrimary(true))
        .withUserConfiguration(ImportSchedulingConfiguration.class);

    @Test
    void providesSeparateDefaultAndStrictObjectMappers() {
        contextRunner.run(context -> {
            JsonMapper standard = context.getBean("jsonMapper", JsonMapper.class);
            JsonMapper strict = context.getBean("importStrictJsonMapper", JsonMapper.class);

            assertThat(strict).isNotSameAs(standard);
            assertThat(standard.readTree("{\"value\":1,\"value\":2}").path("value").asInt()).isEqualTo(2);
            assertThatThrownBy(() -> strict.readTree("{\"value\":1,\"value\":2}"))
                .isInstanceOf(Exception.class);
        });
    }
}
