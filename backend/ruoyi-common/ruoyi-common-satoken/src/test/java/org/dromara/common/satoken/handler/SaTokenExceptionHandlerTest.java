package org.dromara.common.satoken.handler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SaTokenExceptionHandlerTest {

    @Test
    void neverUsesAnExceptionMessageAsTheLogReason() {
        String token = "sensitive-token-value";

        String reason = SaTokenExceptionHandler.safeLogReason(new IllegalStateException(token));

        assertThat(reason).isEqualTo("IllegalStateException");
        assertThat(reason).doesNotContain(token);
    }
}
