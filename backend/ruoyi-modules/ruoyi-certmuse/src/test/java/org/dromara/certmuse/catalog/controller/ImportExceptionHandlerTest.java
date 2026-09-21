package org.dromara.certmuse.catalog.controller;

import org.dromara.certmuse.catalog.support.ImportException;
import org.dromara.certmuse.catalog.domain.bo.CompletedImportBatchQueryBo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.multipart.MultipartException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class ImportExceptionHandlerTest {

    @Test
    void preservesTheBusinessHttpStatusAndBodyCode() {
        var response = new ImportExceptionHandler().handle(
            new ImportException(409, "IMPORT_REQUEST_ID_CONFLICT", "请求号已被不同载荷使用"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(409);
        assertThat(response.getBody().getData().errorCode()).isEqualTo("IMPORT_REQUEST_ID_CONFLICT");
    }

    @Test
    void mapsQueryBindingFailuresToTheExistingImportErrorShape() {
        var binding = new BindException(new CompletedImportBatchQueryBo(), "query");
        binding.rejectValue("pageSize", "Max", "每页数量最大为100");

        var response = new ImportExceptionHandler().handleBinding(binding);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(400);
        assertThat(response.getBody().getData().errorCode()).isEqualTo("IMPORT_CONTEXT_INVALID");
        assertThat(response.getBody().getData().fieldErrors()).singleElement().satisfies(error -> {
            assertThat(error.field()).isEqualTo("pageSize");
            assertThat(error.code()).isEqualTo("OUT_OF_RANGE");
        });
    }

    @Test
    void usesTheSharedRequiredAndFormatFieldErrorCodes() {
        var binding = new BindException(new CompletedImportBatchQueryBo(), "query");
        binding.rejectValue("keyword", "NotBlank", "关键词不能为空");
        binding.rejectValue("pageNum", "typeMismatch", "页码格式不正确");

        var response = new ImportExceptionHandler().handleBinding(binding);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().fieldErrors())
            .extracting("field", "code")
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("keyword", "REQUIRED"),
                org.assertj.core.groups.Tuple.tuple("pageNum", "INVALID_FORMAT")
            );
    }

    @Test
    void keepsTheFrozenFallbackFieldErrorWhenBindingHasNoFields() {
        var response = new ImportExceptionHandler().handleBinding(
            new BindException(new CompletedImportBatchQueryBo(), "query"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().fieldErrors()).singleElement().satisfies(error -> {
            assertThat(error.field()).isEqualTo("request");
            assertThat(error.code()).isEqualTo("INVALID_FORMAT");
        });
    }

    @Test
    void mapsMultipartParsingFailuresToAClientCorrectableImportError() {
        var response = new ImportExceptionHandler().handleRequestException(
            new MultipartException("malformed multipart request")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(400);
        assertThat(response.getBody().getData().errorCode()).isEqualTo("IMPORT_CONTEXT_INVALID");
    }

    @Test
    void identifiesAMissingRequestIdAsARequiredHeader() {
        MissingRequestHeaderException exception = mock(MissingRequestHeaderException.class);
        when(exception.getHeaderName()).thenReturn("X-Request-Id");

        var response = new ImportExceptionHandler().handleRequestException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(400);
        assertThat(response.getBody().getData().errorCode()).isEqualTo("IMPORT_CONTEXT_INVALID");
        assertThat(response.getBody().getData().fieldErrors()).singleElement().satisfies(error -> {
            assertThat(error.field()).isEqualTo("X-Request-Id");
            assertThat(error.code()).isEqualTo("REQUIRED");
            assertThat(error.message()).isEqualTo("请求头不能为空");
        });
    }
}
