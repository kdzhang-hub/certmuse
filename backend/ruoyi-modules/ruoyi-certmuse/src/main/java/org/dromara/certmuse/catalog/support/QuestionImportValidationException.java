package org.dromara.certmuse.catalog.support;

/** Expected content validation failure that must be exposed as an import issue. */
final class QuestionImportValidationException extends RuntimeException {
    private final String code;
    private final String fieldPath;

    QuestionImportValidationException(String code, String fieldPath, String message) {
        super(message);
        this.code = code;
        this.fieldPath = fieldPath;
    }

    String code() {
        return code;
    }

    String fieldPath() {
        return fieldPath;
    }
}
