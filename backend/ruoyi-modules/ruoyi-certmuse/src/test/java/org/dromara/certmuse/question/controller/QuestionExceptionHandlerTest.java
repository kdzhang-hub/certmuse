package org.dromara.certmuse.question.controller;

import java.util.List;
import org.dromara.certmuse.question.domain.vo.QuestionErrorVo;
import org.dromara.certmuse.question.support.QuestionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class QuestionExceptionHandlerTest {

    @Test
    void preservesBusinessErrorsAndBlockingIssues() {
        var issue = new QuestionErrorVo.BlockingIssueVo("QUESTION_NOT_DRAFT", "status", "题目不是草稿");
        var response = new QuestionExceptionHandler().handle(
            new QuestionException(409, "QUESTION_STATE_CONFLICT", "题目状态已变化", List.of(issue))
        );

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(409);
        assertThat(response.getBody().getMsg()).isEqualTo("题目状态已变化");
        assertThat(response.getBody().getData()).satisfies(data -> {
            assertThat(data.errorCode()).isEqualTo("QUESTION_STATE_CONFLICT");
            assertThat(data.blockingIssues()).containsExactly(issue);
            assertThat(data.traceId()).isNull();
        });
    }

}
