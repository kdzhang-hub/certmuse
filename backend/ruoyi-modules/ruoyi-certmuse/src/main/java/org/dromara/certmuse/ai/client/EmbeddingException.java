package org.dromara.certmuse.ai.client;

/** Stable infrastructure failure raised by the embedding provider adapter. */
public class EmbeddingException extends RuntimeException {
    private final String errorCode;

    public EmbeddingException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
