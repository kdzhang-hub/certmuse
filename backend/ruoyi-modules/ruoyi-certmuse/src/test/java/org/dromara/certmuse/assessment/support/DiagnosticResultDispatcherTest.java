package org.dromara.certmuse.assessment.support;

import org.dromara.certmuse.assessment.domain.DiagnosticJobRow;
import org.dromara.certmuse.assessment.domain.DiagnosticSessionRow;
import org.dromara.certmuse.assessment.mapper.DiagnosticMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class DiagnosticResultDispatcherTest {

    @Test
    void productionConstructorIsExplicitlySelectedForSpringInjection() {
        assertThat(Arrays.stream(DiagnosticResultDispatcher.class.getDeclaredConstructors())
            .filter(constructor -> constructor.isAnnotationPresent(Autowired.class)))
            .singleElement()
            .satisfies(constructor -> assertThat(constructor.getParameterCount()).isEqualTo(5));
    }

    @Test
    void returnsWithoutOpeningATransactionWhenNoDurableJobIsReady() {
        DiagnosticMapper mapper = mock(DiagnosticMapper.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        DiagnosticResultDispatcher dispatcher = new DiagnosticResultDispatcher(
            mapper, JsonMapper.builder().build(), transactionManager,
            mock(org.dromara.certmuse.learning.service.MistakeFactRecorder.class));

        dispatcher.dispatch();

        verify(transactionManager, never()).getTransaction(any());
        verify(mapper, never()).succeedJob(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void marksJobFailedWhenTheClaimedPayloadHasNoUsableSession() {
        DiagnosticMapper mapper = mock(DiagnosticMapper.class);
        PlatformTransactionManager transactionManager = transactionManager();
        DiagnosticResultDispatcher dispatcher = new DiagnosticResultDispatcher(
            mapper, JsonMapper.builder().build(), transactionManager,
            mock(org.dromara.certmuse.learning.service.MistakeFactRecorder.class));
        DiagnosticJobRow job = job(44L);
        job.setPayload("{\"sessionId\":\"not-a-number\",\"stage\":\"SUBMITTED\"}");
        when(mapper.claimResultJob()).thenReturn(job);

        dispatcher.dispatch();

        verify(mapper).failJob(org.mockito.ArgumentMatchers.eq(44L), any(String.class));
        verify(mapper, never()).lockSessionForWorker(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void persistsTraceIdWhenPipelineFails() {
        DiagnosticMapper mapper = mock(DiagnosticMapper.class);
        PlatformTransactionManager transactionManager = transactionManager();
        DiagnosticResultDispatcher dispatcher = spy(new DiagnosticResultDispatcher(
            mapper, JsonMapper.builder().build(), transactionManager,
            mock(org.dromara.certmuse.learning.service.MistakeFactRecorder.class)));
        DiagnosticJobRow job = job(42L);
        when(mapper.claimResultJob()).thenReturn(job);
        doThrow(new IllegalStateException("internal detail")).when(dispatcher).process(job);

        dispatcher.dispatch();

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(mapper).failJob(org.mockito.ArgumentMatchers.eq(42L), error.capture());
        assertThat(error.getValue()).matches(
            "traceId=[0-9a-f-]{36};message=诊断结果生成失败");
        assertThat(error.getValue()).doesNotContain("internal detail");
    }

    @Test
    void failurePersistenceDoesNotReplaceOriginalPipelineFailure() {
        DiagnosticMapper mapper = mock(DiagnosticMapper.class);
        PlatformTransactionManager transactionManager = transactionManager();
        DiagnosticResultDispatcher dispatcher = spy(new DiagnosticResultDispatcher(
            mapper, JsonMapper.builder().build(), transactionManager,
            mock(org.dromara.certmuse.learning.service.MistakeFactRecorder.class)));
        DiagnosticJobRow job = job(43L);
        when(mapper.claimResultJob()).thenReturn(job);
        doThrow(new IllegalStateException("pipeline failure")).when(dispatcher).process(job);
        doThrow(new IllegalStateException("persistence failure"))
            .when(mapper).failJob(org.mockito.ArgumentMatchers.eq(43L), any(String.class));

        dispatcher.dispatch();

        verify(mapper).failJob(org.mockito.ArgumentMatchers.eq(43L), any(String.class));
    }

    private PlatformTransactionManager transactionManager() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return manager;
    }

    private DiagnosticJobRow job(long id) {
        DiagnosticJobRow job = new DiagnosticJobRow();
        job.setId(id);
        job.setPayload("{\"sessionId\":\"1\"}");
        return job;
    }
}
