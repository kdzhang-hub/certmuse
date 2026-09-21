package org.dromara.certmuse.learning.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.dromara.certmuse.learning.domain.vo.KnowledgePointDirectoryErrorVo;
import org.dromara.certmuse.learning.support.KnowledgePointDirectoryException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotWritableException;

@Tag("dev")
class KnowledgePointDirectoryExceptionHandlerTest {
    private final KnowledgePointDirectoryExceptionHandler handler = new KnowledgePointDirectoryExceptionHandler();

    @Test
    void preservesBusinessStatusAndAddsTraceOnlyToServerFailures() {
        var invalid = handler.handle(new KnowledgePointDirectoryException(400, "KNOWLEDGE_POINT_LOOKUP_INVALID",
            "请求参数不正确", false, List.of(new KnowledgePointDirectoryErrorVo.FieldErrorVo(
                "ids", "INVALID_FORMAT", "格式错误")), null));
        var missingGoal = handler.handle(new KnowledgePointDirectoryException(409, "LEARNING_GOAL_NOT_ACTIVE",
            "当前没有有效学习目标", false, List.of(), null));
        var failed = handler.handle(new KnowledgePointDirectoryException(500, "KNOWLEDGE_POINT_LOOKUP_SYSTEM_FAILURE",
            "知识点目录暂时不可用", true, List.of(), new IllegalStateException("internal")));

        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat(invalid.getBody().getCode()).isEqualTo(400);
        assertThat(invalid.getBody().getData().traceId()).isNull();
        assertThat(invalid.getBody().getData().fieldErrors()).singleElement().satisfies(error -> {
            assertThat(error.field()).isEqualTo("ids");
            assertThat(error.code()).isEqualTo("INVALID_FORMAT");
        });
        assertThat(missingGoal.getStatusCode().value()).isEqualTo(409);
        assertThat(missingGoal.getBody().getData().errorCode()).isEqualTo("LEARNING_GOAL_NOT_ACTIVE");
        assertThat(failed.getStatusCode().value()).isEqualTo(500);
        assertThat(failed.getBody().getCode()).isEqualTo(500);
        assertThat(failed.getBody().getData().traceId()).isNotBlank();
        assertThat(failed.getBody().getData().fieldErrors()).isEmpty();
    }

    @Test
    void mapsResponseSerializationFailureToTheFrozenSystemError() {
        var response = handler.serializationFailure(new HttpMessageNotWritableException(
            "serialization failure", new IllegalStateException("internal")));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getCode()).isEqualTo(500);
        assertThat(response.getBody().getData().errorCode()).isEqualTo("KNOWLEDGE_POINT_LOOKUP_SYSTEM_FAILURE");
        assertThat(response.getBody().getData().retryable()).isTrue();
        assertThat(response.getBody().getData().traceId()).isNotBlank();
        assertThat(response.getBody().getData().fieldErrors()).isEmpty();
    }
}
