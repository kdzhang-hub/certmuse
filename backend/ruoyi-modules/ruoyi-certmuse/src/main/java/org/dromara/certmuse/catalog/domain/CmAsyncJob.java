package org.dromara.certmuse.catalog.domain;

/**
 * Asynchronous job persistence projection.
 */
public record CmAsyncJob(
    long id,
    String jobType,
    String businessKey,
    String payload,
    int attemptCount,
    int maxAttempts
) {
}
