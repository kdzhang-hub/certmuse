package org.dromara.certmuse.assessment.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.assessment.service.PastPaperService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Claims expired exam sessions and durable result jobs without relying on learner page visits. */
@Slf4j @Component @RequiredArgsConstructor
public class PastPaperDispatcher {
    private final PastPaperService service;

    @Scheduled(fixedDelayString = "${certmuse.past-paper.dispatch-delay-ms:1000}")
    public void dispatch() {
        try {
            service.autoFinishExpired(50);
            service.dispatchResults(20);
            service.processPracticeSubjectiveGrading(20);
        } catch (RuntimeException exception) {
            log.error("Past-paper background dispatch failed", exception);
        }
    }
}
