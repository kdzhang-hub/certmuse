package org.dromara.certmuse.question.controller;

import org.dromara.certmuse.question.domain.vo.CollectionErrorVo;
import org.dromara.certmuse.question.domain.vo.CollectionPublishCheckVo;
import org.dromara.certmuse.question.support.CollectionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class CollectionExceptionHandlerTest {

    @Test
    void preservesCollectionConflictContextAndBlockingIssues() {
        var issue = new CollectionPublishCheckVo.IssueVo("QUESTION_NOT_PUBLISHED", "题目尚未发布", 2);
        var exception = new CollectionException(422, "COLLECTION_PUBLISH_CHECK_FAILED", "发布门禁未通过",
            "revision-2", "4", "revision-3");
        var data = new CollectionErrorVo(exception.getData().errorCode(), exception.getData().currentRevisionId(),
            exception.getData().currentRowVersion(), exception.getData().workingRevisionId(),
            exception.getData().traceId(), List.of(issue));
        var response = new CollectionExceptionHandler().handle(
            new CollectionException(422, data.errorCode(), "发布门禁未通过", data.blockingIssues())
        );

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(422);
        assertThat(response.getBody().getData().errorCode()).isEqualTo("COLLECTION_PUBLISH_CHECK_FAILED");
        assertThat(response.getBody().getData().blockingIssues()).containsExactly(issue);
        assertThat(response.getBody().getData().traceId()).isNull();
    }

}
