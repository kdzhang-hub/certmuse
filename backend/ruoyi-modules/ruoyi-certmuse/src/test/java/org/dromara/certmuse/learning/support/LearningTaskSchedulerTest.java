package org.dromara.certmuse.learning.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.dromara.certmuse.learning.service.LearningTaskService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.annotation.Scheduled;

@Tag("dev")
class LearningTaskSchedulerTest {
    private static final LocalDate DATE = LocalDate.of(2026, 8, 17);

    @Test
    void usesShanghaiMidnightAndTwoHundredGoalPages() throws Exception {
        Scheduled scheduled = LearningTaskScheduler.class.getMethod("supplementActiveGoals")
            .getAnnotation(Scheduled.class);
        assertThat(scheduled.zone()).isEqualTo("Asia/Shanghai");
        assertThat(scheduled.cron()).contains("0 0 0 * * *");
        assertThat(LearningTaskScheduler.PAGE_SIZE).isEqualTo(200);
    }

    @Test
    void oneGoalFailureDoesNotPreventTheNextGoal() {
        LearningTaskService service = Mockito.mock(LearningTaskService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-16T16:00:00Z"), ZoneId.of("Asia/Shanghai"));
        LearningTaskScheduler scheduler = new LearningTaskScheduler(service, clock);
        when(service.activeGoalIds(0, 200)).thenReturn(List.of(10L, 11L));
        doThrow(new IllegalStateException("expected test failure")).when(service).supplementScheduled(10L, DATE);

        scheduler.supplementActiveGoals();

        verify(service).supplementScheduled(10L, DATE);
        verify(service).supplementScheduled(11L, DATE);
    }
}
