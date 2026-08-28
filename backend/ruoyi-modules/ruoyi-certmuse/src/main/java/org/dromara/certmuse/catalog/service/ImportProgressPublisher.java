package org.dromara.certmuse.catalog.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.common.core.enums.PushSourceEnum;
import org.dromara.common.core.enums.PushTypeEnum;
import org.dromara.system.api.MessageService;
import org.dromara.system.api.domain.PushPayloadDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Publishes throttled, user-scoped import progress events after persisted state changes.
 */
@Slf4j
@Component
public class ImportProgressPublisher {

    private static final int MAX_TRACKED_BATCHES = 10_000;
    private static final Set<String> TERMINAL = Set.of(
        "waiting_confirm", "completed", "partial_failed", "failed", "cancelled"
    );
    private final ImportMapper repository;
    private final MessageService messageService;
    private final Counter publishedEvents;
    private final Counter failedEvents;
    private final Map<Long, PublishedState> published = Collections.synchronizedMap(
        new LinkedHashMap<>(128, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, PublishedState> eldest) {
                return size() > MAX_TRACKED_BATCHES;
            }
        }
    );

    public ImportProgressPublisher(ImportMapper repository, MessageService messageService, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.messageService = messageService;
        this.publishedEvents = meterRegistry.counter("certmuse.import.events.published");
        this.failedEvents = meterRegistry.counter("certmuse.import.events.failed");
    }

    public synchronized void publish(long batchId) {
        try {
            CmImportBatch batch = repository.selectById(batchId);
            if (batch == null || batch.createBy() == null) {
                return;
            }
            PublishedState next = state(batch);
            PublishedState previous = published.get(batchId);
            if (next.equals(previous)) {
                return;
            }
            boolean terminal = TERMINAL.contains(batch.status());
            PushPayloadDTO payload = PushPayloadDTO.of(
                terminal ? PushTypeEnum.MESSAGE : PushTypeEnum.CUSTOM,
                PushSourceEnum.BACKEND,
                terminal ? terminalMessage(batch) : "导入进度已更新",
                data(batch),
                "/certmuse/catalog/import?batchId=" + batch.id()
            );
            messageService.publishMessage(List.of(batch.createBy()), payload);
            published.put(batchId, next);
            publishedEvents.increment();
        } catch (Exception exception) {
            failedEvents.increment();
            log.warn("Import progress publish failed, batchId={}", batchId, exception);
        }
    }

    private static PublishedState state(CmImportBatch batch) {
        int bucket = batch.progressPercent() == null ? 0 : batch.progressPercent().intValue() / 5;
        return new PublishedState(batch.status(), batch.currentStage(), bucket);
    }

    private static Map<String, Object> data(CmImportBatch batch) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventType", "certmuse.import.progress");
        data.put("batchId", Long.toString(batch.id()));
        data.put("importType", batch.importType());
        data.put("status", batch.status());
        data.put("currentStage", batch.currentStage());
        data.put("progressPercent", value(batch.progressPercent()));
        data.put("validCount", batch.validCount());
        data.put("warningCount", batch.warningCount());
        data.put("failedCount", batch.failedCount());
        if (batch.traceId() != null) {
            data.put("failureTraceId", batch.traceId());
        }
        return data;
    }

    private static BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String terminalMessage(CmImportBatch batch) {
        return switch (batch.status()) {
            case "waiting_confirm" -> "导入预检已完成，请确认导入";
            case "completed" -> "导入已完成";
            case "partial_failed" -> "导入已完成，部分记录失败";
            case "cancelled" -> "导入已取消";
            default -> "导入失败，请查看失败信息";
        };
    }

    private record PublishedState(String status, String stage, int progressBucket) {
    }
}
