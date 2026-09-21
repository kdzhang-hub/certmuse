package org.dromara.certmuse.catalog.controller;

import org.dromara.certmuse.catalog.domain.bo.QualificationWriteBo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingRequestHeaderException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class QualificationVersionExceptionHandlerTest {

    private final QualificationVersionExceptionHandler handler = new QualificationVersionExceptionHandler();

    @Test
    void mapsBindingErrorsThroughTheSharedSafeFieldMapper() {
        BindException binding = new BindException(new QualificationWriteBo(), "command");
        binding.rejectValue("certificationName", "NotBlank", "资格名称不能为空");
        binding.rejectValue("sortOrder", "Min", "排序值不能小于0");
        binding.rejectValue("certificationCode", "Pattern", "资格编码格式不正确");

        var response = handler.handleInvalidRequest(binding);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(400);
        assertThat(response.getBody().getData().fieldErrors())
            .extracting("field", "message")
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("certificationName", "资格名称不能为空"),
                org.assertj.core.groups.Tuple.tuple("sortOrder", "排序值不能小于0"),
                org.assertj.core.groups.Tuple.tuple("certificationCode", "资格编码格式不正确")
            );
    }

    @Test
    void preservesTheSpecificMissingHeaderField() {
        MissingRequestHeaderException exception = mock(MissingRequestHeaderException.class);
        when(exception.getHeaderName()).thenReturn("X-Request-Id");

        var response = handler.handleInvalidRequest(exception);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().fieldErrors()).singleElement().satisfies(error -> {
            assertThat(error.field()).isEqualTo("X-Request-Id");
            assertThat(error.message()).isEqualTo("请求头不能为空");
        });
    }

    @Test
    void usesASafeRequestErrorForMalformedJson() {
        var response = handler.handleInvalidRequest(mock(HttpMessageNotReadableException.class));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMsg()).isEqualTo("请求参数校验失败");
        assertThat(response.getBody().getData().fieldErrors()).singleElement().satisfies(error -> {
            assertThat(error.field()).isEqualTo("request");
            assertThat(error.message()).isEqualTo("请求体格式不正确");
        });
    }

    @Test
    void keepsAnEmptyFieldListWhenBindingHasNoFieldErrors() {
        var response = handler.handleInvalidRequest(new BindException(new QualificationWriteBo(), "command"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().fieldErrors()).isEmpty();
    }

    @Test
    void frozenFieldErrorJsonDoesNotGainACodeProperty() throws Exception {
        BindException binding = new BindException(new QualificationWriteBo(), "command");
        binding.rejectValue("sortOrder", "Min", "排序值不能小于0");

        String json = JsonMapper.builder().build().writeValueAsString(
            handler.handleInvalidRequest(binding).getBody().getData());

        assertThat(json).contains("\"field\":\"sortOrder\"", "\"message\":\"排序值不能小于0\"")
            .doesNotContain("\"code\"");
    }
}
