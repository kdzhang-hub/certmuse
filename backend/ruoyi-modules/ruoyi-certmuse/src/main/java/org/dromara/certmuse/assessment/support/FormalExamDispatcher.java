package org.dromara.certmuse.assessment.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.assessment.domain.FormalExamJobRow;
import org.dromara.certmuse.assessment.mapper.FormalExamMapper;
import org.dromara.certmuse.assessment.service.FormalExamEngine;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Advances queued formal-exam result jobs outside HTTP transactions. */
@Component
@RequiredArgsConstructor
@Slf4j
public class FormalExamDispatcher {
    private final FormalExamEngine engine;
    private final FormalExamMapper mapper;

    @Scheduled(fixedDelayString = "${certmuse.formal-exam.dispatch-delay-ms:1000}")
    public void dispatch() {
        for (int i = 0; i < 20; i++) {
            FormalExamJobRow job = mapper.claimJob();
            if (job == null) return;
            try {
                if (engine.processJob(job)) mapper.markJobSucceeded(job.getId());
                else mapper.requeueJob(job.getId());
            } catch (Exception exception) {
                log.error("Formal-exam result generation failed, jobId={}", job.getId(), exception);
                mapper.markJobFailed(job.getId(), "result_generation_failed");
            }
        }
    }
}
