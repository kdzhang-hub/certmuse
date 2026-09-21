package org.dromara.certmuse.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.exception.NotPermissionException;
import java.util.List;
import org.dromara.common.core.domain.R;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

@Tag("dev")
class CertMuseExceptionHandlerTest {
    private final CertMuseExceptionHandler handler = new CertMuseExceptionHandler();

    @Test
    void keepsHttpAndEnvelopeStatusAlignedForDomainErrors() {
        ResponseEntity<R<CertMuseApiError>> response = handler.handleApi(
            new CertMuseApiException(409, "RESOURCE_VERSION_CONFLICT", "资源已更新",
                true, null, List.of(), null, null));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getCode()).isEqualTo(409);
        assertThat(response.getBody().getData().errorCode()).isEqualTo("RESOURCE_VERSION_CONFLICT");
        assertThat(response.getBody().getData().retryable()).isTrue();
    }

    @Test
    void mapsUnreadableJsonToRequestErrorInsteadOfMissingHeader() {
        var cause = new HttpMessageNotReadableException("invalid json", new MockHttpInputMessage(new byte[0]));
        ResponseEntity<R<CertMuseApiError>> response = handler.handleInvalid(cause);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo(400);
        assertThat(response.getBody().getData().errorCode()).isEqualTo("REQUEST_INVALID");
        assertThat(response.getBody().getData().fieldErrors()).isEmpty();
    }

    @Test
    void preservesCauseAndMapsPermissionAndUnexpectedFailuresSafely() {
        var cause = new IllegalStateException("database secret");
        var translated = new CertMuseApiException(
            500, "TEST", "test", false, null, List.of(), null, cause);
        ResponseEntity<R<Void>> forbidden = handler.handleForbidden(
            new NotPermissionException("secret:permission"));
        ResponseEntity<R<CertMuseApiError>> failure = handler.handleUnexpected(cause);

        assertThat(translated).hasCause(cause);
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(forbidden.getBody().getCode()).isEqualTo(403);
        assertThat(forbidden.getBody().getData()).isNull();
        assertThat(failure.getStatusCode().value()).isEqualTo(500);
        assertThat(failure.getBody().getMsg()).doesNotContain("database secret");
        assertThat(failure.getBody().getData().traceId()).isNotBlank();
    }

    @Test
    void preservesProvidedTraceIdAndLeavesExpectedClientFailuresWithoutOne() {
        var clientFailure = handler.handleApi(new CertMuseApiException(404, "RESOURCE_NOT_FOUND", "资源不存在"));
        var serverFailure = handler.handleApi(new CertMuseApiException(
            503, "DEPENDENCY_UNAVAILABLE", "依赖暂时不可用", true, "trace-existing",
            List.of(), null, new IllegalStateException("internal")));

        assertThat(clientFailure.getBody().getData().traceId()).isNull();
        assertThat(serverFailure.getBody().getData().traceId()).isEqualTo("trace-existing");
    }
}
