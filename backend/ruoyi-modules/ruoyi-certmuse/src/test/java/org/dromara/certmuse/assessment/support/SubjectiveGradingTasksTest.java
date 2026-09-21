package org.dromara.certmuse.assessment.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.dromara.certmuse.assessment.domain.AiGradingTaskRow;
import org.dromara.certmuse.assessment.mapper.AiGradingMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Regression coverage for propagating a terminal rubric task failure to the exam workflow. */
@Tag("dev")
class SubjectiveGradingTasksTest {
    private final AiGradingMapper mapper = mock(AiGradingMapper.class);
    private final SubjectiveGradingTasks tasks = new SubjectiveGradingTasks(mapper, JsonMapper.builder().build());

    @Test
    void returnsFailedWhenTheRevisionRubricTaskHasFailed() {
        AiGradingTaskRow task = new AiGradingTaskRow();
        task.setStatus("failed");
        task.setErrorCode("AI_PROVIDER_TIMEOUT");
        when(mapper.selectTask("RUBRIC:11")).thenReturn(task);

        SubjectiveGradingTasks.TaskState state = tasks.ensure(11L, 12L, 13L,
            "{\"stem\":\"题干\"}", "{\"answer\":{\"value\":\"参考答案\"}}", "{\"items\":[]}", "学生答案");

        assertThat(state.status()).isEqualTo("FAILED");
        assertThat(state.errorCode()).isEqualTo("AI_PROVIDER_TIMEOUT");
        verify(mapper, never()).insertTask(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }
}
