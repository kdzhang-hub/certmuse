package org.dromara.certmuse.ai.client;

/** Safe provider failure surfaced to durable grading workers. */
public class AiStructuredCompletionException extends RuntimeException {
    private final String errorCode;

    public AiStructuredCompletionException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
