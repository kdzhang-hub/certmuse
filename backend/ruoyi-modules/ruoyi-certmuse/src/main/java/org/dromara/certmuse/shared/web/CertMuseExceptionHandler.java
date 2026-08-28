package org.dromara.certmuse.shared.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import java.util.List;
import org.dromara.common.core.domain.R;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(CertMuseAdviceOrder.MODULE)
@RestControllerAdvice(basePackages = "org.dromara.certmuse")
public class CertMuseExceptionHandler {
    @ExceptionHandler(CertMuseApiException.class)
    public ResponseEntity<R<CertMuseApiError>> handleApi(CertMuseApiException exception) {
        return CertMuseErrorResponses.fail(exception);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
        MissingRequestHeaderException.class, MissingServletRequestParameterException.class,
        HttpMessageNotReadableException.class})
    public ResponseEntity<R<CertMuseApiError>> handleInvalid(Exception exception) {
        List<ApiFieldError> fields;
        if (exception instanceof MethodArgumentNotValidException method) {
            fields = ApiFieldErrors.from(method.getBindingResult().getFieldErrors());
        } else if (exception instanceof BindException bind) {
            fields = ApiFieldErrors.from(bind.getFieldErrors());
        } else if (exception instanceof MissingRequestHeaderException header) {
            fields = List.of(new ApiFieldError(header.getHeaderName(), "REQUIRED", "请求头不能为空"));
        } else if (exception instanceof MissingServletRequestParameterException parameter) {
            fields = List.of(new ApiFieldError(parameter.getParameterName(), "REQUIRED", "请求参数不能为空"));
        } else {
            fields = List.of();
        }
        return CertMuseErrorResponses.fail(new CertMuseApiException(
            400, "REQUEST_INVALID", "请求参数格式不正确", false, null, fields, null, exception));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<R<CertMuseApiError>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return invalid(exception, List.of(new ApiFieldError(
            exception.getName(), "INVALID_FORMAT", "请求参数格式不正确")));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<R<CertMuseApiError>> handleMethodValidation(
        HandlerMethodValidationException exception
    ) {
        if (exception.isForReturnValue()) {
            return handleUnexpected(exception);
        }
        List<ApiFieldError> fields = exception.getParameterValidationResults().stream()
            .flatMap(result -> result.getResolvableErrors().stream().map(error -> ApiFieldErrors.from(
                result.getMethodParameter().getParameterName(), error)))
            .toList();
        return invalid(exception, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<CertMuseApiError>> handleConstraintViolation(
        ConstraintViolationException exception
    ) {
        boolean requestParameterFailure = exception.getConstraintViolations().stream()
            .allMatch(CertMuseExceptionHandler::isRequestParameterViolation);
        if (!requestParameterFailure) {
            return handleUnexpected(exception);
        }
        List<ApiFieldError> fields = exception.getConstraintViolations().stream()
            .map(CertMuseExceptionHandler::fromConstraintViolation)
            .toList();
        return invalid(exception, fields);
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<R<Void>> handleUnauthorized(NotLoginException exception) {
        return CertMuseErrorResponses.fail(401, "登录状态异常，请重新登录");
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public ResponseEntity<R<Void>> handleForbidden(RuntimeException exception) {
        return CertMuseErrorResponses.fail(403, "没有访问权限");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<CertMuseApiError>> handleUnexpected(Exception exception) {
        return CertMuseErrorResponses.fail(new CertMuseApiException(
            500, "INTERNAL_SERVER_ERROR", "系统暂时无法处理请求", true, null, List.of(), null, exception));
    }

    private ResponseEntity<R<CertMuseApiError>> invalid(Exception exception, List<ApiFieldError> fields) {
        return CertMuseErrorResponses.fail(new CertMuseApiException(
            400, "REQUEST_INVALID", "请求参数格式不正确", false, null, fields, null, exception));
    }

    private static ApiFieldError fromConstraintViolation(ConstraintViolation<?> violation) {
        String field = null;
        for (var node : violation.getPropertyPath()) {
            if (node.getKind() == ElementKind.PARAMETER || node.getKind() == ElementKind.PROPERTY) {
                field = node.getName();
            }
        }
        String validationCode = violation.getConstraintDescriptor().getAnnotation()
            .annotationType().getSimpleName();
        return ApiFieldErrors.from(field, validationCode, violation.getMessage());
    }

    private static boolean isRequestParameterViolation(ConstraintViolation<?> violation) {
        return java.util.stream.StreamSupport.stream(violation.getPropertyPath().spliterator(), false)
            .anyMatch(node -> node.getKind() == ElementKind.PARAMETER
                || node.getKind() == ElementKind.CROSS_PARAMETER);
    }
}
