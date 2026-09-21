package org.dromara.certmuse.catalog.controller;

import org.dromara.certmuse.catalog.domain.bo.SyllabusPublishedDateUpdateBo;
import org.dromara.certmuse.catalog.support.SyllabusPublishedDateException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingRequestHeaderException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class SyllabusPublishedDateExceptionHandlerTest {

    private final SyllabusPublishedDateExceptionHandler handler = new SyllabusPublishedDateExceptionHandler();

    @Test
    void preservesDedicatedPublishedDateBusinessFailureContract() {
        var response = handler.handle(new SyllabusPublishedDateException(
            409, "IDEMPOTENCY_KEY_CONFLICT", "请求号已被使用"
        ));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(409);
        assertThat(response.getBody().getData().errorCode()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
    }

    @Test
    void returnsSafeFieldErrorsForValidationBinding() {
        BindException binding = new BindException(new SyllabusPublishedDateUpdateBo(), "command");
        binding.rejectValue("publishedDate", "NotNull", "发布日期不能为空");

        var response = handler.handleInvalidRequest(binding);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().errorCode()).isEqualTo("SYLLABUS_PUBLISHED_DATE_INVALID");
        assertThat(response.getBody().getData().fieldErrors()).singleElement().satisfies(error -> {
            assertThat(error.field()).isEqualTo("publishedDate");
            assertThat(error.code()).isEqualTo("REQUIRED");
            assertThat(error.message()).isEqualTo("发布日期不能为空");
        });
    }

    @Test
    void identifiesTheMissingIdempotencyHeader() {
        MissingRequestHeaderException exception = mock(MissingRequestHeaderException.class);
        when(exception.getHeaderName()).thenReturn("X-Request-Id");

        var response = handler.handleInvalidRequest(exception);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().fieldErrors()).singleElement().satisfies(error -> {
            assertThat(error.field()).isEqualTo("requestId");
            assertThat(error.code()).isEqualTo("REQUIRED");
            assertThat(error.message()).isEqualTo("X-Request-Id不能为空");
        });
    }

    @Test
    void mapsUnreadableDatePayloadToThePublishedDateField() {
        var response = handler.handleInvalidRequest(mock(HttpMessageNotReadableException.class));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().fieldErrors()).singleElement().satisfies(error -> {
            assertThat(error.field()).isEqualTo("publishedDate");
            assertThat(error.code()).isEqualTo("INVALID_FORMAT");
            assertThat(error.message()).isEqualTo("发布日期格式不正确");
        });
    }
}
