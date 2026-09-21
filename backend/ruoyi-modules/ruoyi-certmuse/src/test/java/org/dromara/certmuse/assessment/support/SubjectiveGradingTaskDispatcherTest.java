package org.dromara.certmuse.assessment.support;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.dromara.certmuse.ai.client.AiStructuredCompletionClient;
import org.dromara.certmuse.ai.config.CertMuseAiProperties;
import org.dromara.certmuse.assessment.mapper.AiGradingMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Regression coverage for recovering a grading task abandoned by a stopped worker. */
@Tag("dev")
class SubjectiveGradingTaskDispatcherTest {
    @Test
    void reclaimsExpiredProcessingTasksBeforeClaimingNewWork() {
        AiGradingMapper mapper = mock(AiGradingMapper.class);
        CertMuseAiProperties properties = new CertMuseAiProperties();
        properties.setGradingLeaseTimeout(Duration.ofMinutes(2));
        when(mapper.reclaimExpiredTasks(120L)).thenReturn(1);

        new SubjectiveGradingTaskDispatcher(mapper, mock(AiStructuredCompletionClient.class), properties,
            JsonMapper.builder().build()).dispatch();

        verify(mapper).reclaimExpiredTasks(120L);
        verify(mapper).claimTask();
    }
}
