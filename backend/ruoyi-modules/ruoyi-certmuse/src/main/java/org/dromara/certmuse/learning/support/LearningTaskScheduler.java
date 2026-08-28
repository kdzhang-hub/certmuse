package org.dromara.certmuse.learning.support;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.learning.service.LearningTaskService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Midnight task-pool replenishment; each goal executes in an isolated transaction. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LearningTaskScheduler {
    static final int PAGE_SIZE = 200;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final LearningTaskService service;
    private final Clock clock;

    @Scheduled(cron = "${certmuse.learning-task.supplement-cron:0 0 0 * * *}", zone = "Asia/Shanghai")
    public void supplementActiveGoals() {
        LocalDate date = LocalDate.now(clock.withZone(ZONE));
        int offset = 0;
        while (true) {
            List<Long> goalIds = service.activeGoalIds(offset, PAGE_SIZE);
            for (Long goalId : goalIds) {
                try {
                    service.supplementScheduled(goalId, date);
                } catch (RuntimeException exception) {
                    log.error("Scheduled learning-task supplement failed, goalId={}, businessDate={}",
                        goalId, date, exception);
                }
            }
            if (goalIds.size() < PAGE_SIZE) return;
            offset += PAGE_SIZE;
        }
    }
}
