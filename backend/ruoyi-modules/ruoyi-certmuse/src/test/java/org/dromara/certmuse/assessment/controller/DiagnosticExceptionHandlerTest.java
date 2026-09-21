package org.dromara.certmuse.assessment.controller;

import org.dromara.certmuse.assessment.support.DiagnosticException;
import org.dromara.certmuse.shared.web.CertMuseApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class DiagnosticExceptionHandlerTest {
    private final DiagnosticExceptionHandler handler = new DiagnosticExceptionHandler();

    @Test
    void emitsFrozenDiagnosticErrorBody() {
        var response = handler.handle(new DiagnosticException(409, "DIAGNOSTIC_STATE_CONFLICT", "不可编辑", false, "WAIT_PROCESSING"));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getData().errorCode()).isEqualTo("DIAGNOSTIC_STATE_CONFLICT");
        assertThat(response.getBody().getData().nextAction()).isEqualTo("WAIT_PROCESSING");
    }

    @Test
    void preservesCauseWithoutExposingItInFrozenBody() {
        var cause = new IllegalStateException("internal database detail");
        var exception = new DiagnosticException(
            500, "DIAGNOSTIC_SYSTEM_FAILURE", "读取诊断题目失败", true, null, cause);

        var response = handler.handle(exception);

        assertThat(exception).isInstanceOf(CertMuseApiException.class);
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getCode()).isEqualTo(500);
        assertThat(response.getBody().getMsg()).isEqualTo("读取诊断题目失败");
        assertThat(response.getBody().getData().errorCode()).isEqualTo("DIAGNOSTIC_SYSTEM_FAILURE");
        assertThat(response.getBody().getData().traceId()).isNotBlank();
        assertThat(response.getBody().toString()).doesNotContain("internal database detail");
    }
}
