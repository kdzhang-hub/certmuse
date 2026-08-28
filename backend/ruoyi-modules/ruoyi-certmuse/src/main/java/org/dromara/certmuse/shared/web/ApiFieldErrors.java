package org.dromara.certmuse.shared.web;

import java.util.List;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.FieldError;

/** Maps Jakarta/Spring validation failures to stable API field error codes. */
public final class ApiFieldErrors {
    private ApiFieldErrors() {
    }

    public static List<ApiFieldError> from(List<FieldError> errors) {
        return errors.stream().map(ApiFieldErrors::from).toList();
    }

    public static ApiFieldError from(FieldError error) {
        return from(error.getField(), error.getCode(), error.getDefaultMessage());
    }

    public static ApiFieldError from(String field, MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        String validationCode = codes == null || codes.length == 0 ? null : codes[0];
        return from(field, validationCode, error.getDefaultMessage());
    }

    public static ApiFieldError from(String field, String validationCode, String message) {
        String simpleCode = simpleValidationCode(validationCode);
        String code = switch (simpleCode) {
            case "NotBlank", "NotEmpty", "NotNull" -> "REQUIRED";
            case "Min", "Max", "DecimalMin", "DecimalMax", "Positive", "PositiveOrZero",
                 "Negative", "NegativeOrZero", "Size" -> "OUT_OF_RANGE";
            default -> "INVALID_FORMAT";
        };
        String safeField = field == null || field.isBlank() ? "request" : field;
        String safeMessage = message == null ? "请求参数格式不正确" : message;
        return new ApiFieldError(safeField, code, safeMessage);
    }

    private static String simpleValidationCode(String validationCode) {
        if (validationCode == null) {
            return "";
        }
        for (String candidate : validationCode.split("\\.")) {
            if (switch (candidate) {
                case "NotBlank", "NotEmpty", "NotNull", "Min", "Max", "DecimalMin", "DecimalMax",
                     "Positive", "PositiveOrZero", "Negative", "NegativeOrZero", "Size" -> true;
                default -> false;
            }) {
                return candidate;
            }
        }
        return validationCode;
    }
}
