package org.dromara.certmuse.catalog.support;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.dromara.certmuse.catalog.service.ImportProgressPublisher;

import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.system.api.MessageService;
import org.dromara.system.api.domain.PushPayloadDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class ImportProgressPublisherTest {

    @Test
    void publishesOnlyWhenProgressCrossesFivePercentBucket() {
        ImportMapper repository = mock(ImportMapper.class);
        MessageService messages = mock(MessageService.class);
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        ImportProgressPublisher publisher = new ImportProgressPublisher(repository, messages, metrics);
        when(repository.selectById(7L))
            .thenReturn(batch("validating", "validate_mapping", "10.00"))
            .thenReturn(batch("validating", "validate_mapping", "14.99"))
            .thenReturn(batch("validating", "validate_mapping", "15.00"));

        publisher.publish(7L);
        publisher.publish(7L);
        publisher.publish(7L);

        verify(messages, org.mockito.Mockito.times(2)).publishMessage(eq(java.util.List.of(42L)), any(PushPayloadDTO.class));
        org.assertj.core.api.Assertions.assertThat(metrics.counter("certmuse.import.events.published").count())
            .isEqualTo(2);
    }

    @Test
    void doesNotPublishWhenBatchHasNoCreator() {
        ImportMapper repository = mock(ImportMapper.class);
        MessageService messages = mock(MessageService.class);
        ImportProgressPublisher publisher = new ImportProgressPublisher(
            repository, messages, new SimpleMeterRegistry()
        );
        when(repository.selectById(7L)).thenReturn(batch("completed", "done", "100.00", null));

        publisher.publish(7L);

        verify(messages, never()).publishMessage(any(), any());
    }

    @Test
    void retriesUnchangedTerminalStateAfterPublishFailure() {
        ImportMapper repository = mock(ImportMapper.class);
        MessageService messages = mock(MessageService.class);
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        ImportProgressPublisher publisher = new ImportProgressPublisher(repository, messages, metrics);
        when(repository.selectById(7L)).thenReturn(batch("completed", "done", "100.00"));
        doThrow(new IllegalStateException("redis unavailable"))
            .doNothing()
            .when(messages).publishMessage(eq(java.util.List.of(42L)), any(PushPayloadDTO.class));

        publisher.publish(7L);
        publisher.publish(7L);

        verify(messages, times(2)).publishMessage(eq(java.util.List.of(42L)), any(PushPayloadDTO.class));
        org.assertj.core.api.Assertions.assertThat(metrics.counter("certmuse.import.events.failed").count())
            .isEqualTo(1);
    }

    private static CmImportBatch batch(String status, String stage, String progress) {
        return batch(status, stage, progress, 42L);
    }

    private static CmImportBatch batch(String status, String stage, String progress, Long createBy) {
        return new CmImportBatch(
            7L, "req", null, 1L, 2L, "object", "hash", 10L, "document_chunk", "1", "{}",
            status, stage, new BigDecimal(progress), 8, 1, 0, 8, null,
            OffsetDateTime.now(), OffsetDateTime.now(), null, createBy, 1L
        );
    }
}
