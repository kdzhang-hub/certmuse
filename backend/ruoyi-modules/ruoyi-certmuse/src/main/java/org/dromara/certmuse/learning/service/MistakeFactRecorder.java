package org.dromara.certmuse.learning.service;

/** Reusable error-fact writer for formal non-correction learning attempts. */
public interface MistakeFactRecorder {
    void record(long userId, long goalId, long attemptId, long knowledgePointId, String evidenceGroupKey);
}
