package org.dromara.certmuse.ai.client;

/** Receives provider stream tokens without exposing provider response models. */
public interface AiModelStreamListener {
    void onDelta(String delta);
    void onCompleted(String finishReason);
    void onFailure(String errorCode, Throwable cause);
}
