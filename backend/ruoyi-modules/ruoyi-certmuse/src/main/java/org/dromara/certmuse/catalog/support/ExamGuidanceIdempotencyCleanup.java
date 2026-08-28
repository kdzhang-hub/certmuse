package org.dromara.certmuse.catalog.support;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Removes only expired U16 idempotency records; business audit facts remain immutable. */
@Component
@RequiredArgsConstructor
public class ExamGuidanceIdempotencyCleanup {
    private final JdbcTemplate jdbc;

    @Scheduled(cron = "${certmuse.exam-guidance.idempotency-cleanup-cron:0 15 3 * * *}", zone = "Asia/Shanghai")
    public void cleanup() {
        jdbc.update("delete from cm_idempotency_record where action_code like 'EXAM_GUIDANCE_%' and expires_time < now() and status in ('succeeded','failed','processing')");
    }
}
