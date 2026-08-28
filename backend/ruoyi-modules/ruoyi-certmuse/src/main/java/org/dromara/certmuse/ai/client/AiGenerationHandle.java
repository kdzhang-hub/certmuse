package org.dromara.certmuse.ai.client;

/** Cancels an in-flight provider generation. */
@FunctionalInterface
public interface AiGenerationHandle {
    void cancel();
}
