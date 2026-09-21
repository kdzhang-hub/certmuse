package org.dromara.certmuse.assessment.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.dromara.certmuse.assessment.support.SimulationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class SimulationExceptionHandlerTest {
    private final SimulationExceptionHandler handler = new SimulationExceptionHandler();

    @Test
    void preservesFrozenGatedErrorsWithoutATraceId() {
        var response = handler.handle(new SimulationException(
            503, "SIMULATION_NOT_OPEN", "模拟考试暂未开放", true));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().getCode()).isEqualTo(503);
        assertThat(response.getBody().getData().errorCode()).isEqualTo("SIMULATION_NOT_OPEN");
        assertThat(response.getBody().getData().retryable()).isTrue();
        assertThat(response.getBody().getData().traceId()).isNull();
    }

    @Test
    void redactsUnexpectedFailuresAndCreatesOneSafeTraceIdAtTheBoundary() {
        IllegalStateException cause = new IllegalStateException("internal database detail");
        SimulationException failure = new SimulationException(500, "SIMULATION_SYSTEM_FAILURE",
            "模拟试卷读取失败", true, List.of(), null, cause);

        var response = handler.handle(failure);

        assertThat(failure.getCause()).isSameAs(cause);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getMsg()).isEqualTo("模拟试卷读取失败");
        assertThat(response.getBody().getData().errorCode()).isEqualTo("SIMULATION_SYSTEM_FAILURE");
        assertThat(response.getBody().getData().traceId()).isNotBlank();
        assertThat(response.getBody().toString()).doesNotContain("internal database detail");
    }
}
