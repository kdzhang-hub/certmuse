package org.dromara.certmuse.catalog.controller;

import org.dromara.certmuse.catalog.domain.vo.TextbookErrorVo;
import org.dromara.certmuse.catalog.support.TextbookException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class TextbookExceptionHandlerTest {

    @Test
    void preservesBusinessStatusRetryabilityAndFieldErrors() {
        var fieldErrors = List.of(new TextbookErrorVo.FieldErrorVo("title", "REQUIRED", "标题不能为空"));
        var exception = new TextbookException(409, "TEXTBOOK_VERSION_CONFLICT", "教材已被更新", true, fieldErrors);

        var response = new TextbookExceptionHandler().handle(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(409);
        assertThat(response.getBody().getMsg()).isEqualTo("教材已被更新");
        assertThat(response.getBody().getData()).satisfies(data -> {
            assertThat(data.errorCode()).isEqualTo("TEXTBOOK_VERSION_CONFLICT");
            assertThat(data.retryable()).isTrue();
            assertThat(data.traceId()).isNull();
            assertThat(data.fieldErrors()).containsExactlyElementsOf(fieldErrors);
        });
    }

}
