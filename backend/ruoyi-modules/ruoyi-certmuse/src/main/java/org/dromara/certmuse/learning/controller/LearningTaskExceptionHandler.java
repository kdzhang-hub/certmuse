package org.dromara.certmuse.learning.controller;

import java.util.List;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.dromara.certmuse.learning.domain.vo.LearningTaskErrorVo;
import org.dromara.certmuse.learning.support.LearningTaskException;
import org.dromara.certmuse.shared.web.ApiFieldErrors;
import org.dromara.certmuse.shared.web.CertMuseAdviceOrder;
import org.dromara.certmuse.shared.web.CertMuseErrorResponses;
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

/** Stabilizes U10 expected failures without intercepting unknown exceptions. */
@Order(CertMuseAdviceOrder.DOMAIN)
@RestControllerAdvice(assignableTypes = LearningTaskController.class)
public class LearningTaskExceptionHandler {
    @ExceptionHandler(LearningTaskException.class)
    public ResponseEntity<R<LearningTaskErrorVo>> handle(LearningTaskException exception) {
        return CertMuseErrorResponses.fail(exception, traceId -> new LearningTaskErrorVo(
            exception.errorCode(), exception.retryable(), traceId,
            exception.apiFieldErrors().stream().map(error -> new LearningTaskErrorVo.FieldErrorVo(
                error.field(), error.code(), error.message())).toList(), exception.details()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
        HttpMessageNotReadableException.class, MissingRequestHeaderException.class,
        MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
        HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ResponseEntity<R<LearningTaskErrorVo>> handleInvalid(Exception exception) {
        List<LearningTaskErrorVo.FieldErrorVo> errors;
        if (exception instanceof MethodArgumentNotValidException method) {
            errors = fields(ApiFieldErrors.from(method.getBindingResult().getFieldErrors()));
        } else if (exception instanceof BindException bind) {
            errors = fields(ApiFieldErrors.from(bind.getFieldErrors()));
        } else if (exception instanceof MissingRequestHeaderException header) {
            errors = List.of(new LearningTaskErrorVo.FieldErrorVo(header.getHeaderName(), "REQUIRED", "请求头不能为空"));
        } else if (exception instanceof MissingServletRequestParameterException parameter) {
            errors = List.of(new LearningTaskErrorVo.FieldErrorVo(parameter.getParameterName(), "REQUIRED", "请求参数不能为空"));
        } else if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            errors = List.of(new LearningTaskErrorVo.FieldErrorVo(mismatch.getName(), "INVALID_FORMAT", "请求参数格式不正确"));
        } else if (exception instanceof HandlerMethodValidationException validation) {
            errors = validation.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream().map(error -> ApiFieldErrors.from(
                    result.getMethodParameter().getParameterName(), error)))
                .map(error -> new LearningTaskErrorVo.FieldErrorVo(error.field(), error.code(), error.message()))
                .toList();
        } else if (exception instanceof ConstraintViolationException violations) {
            errors = violations.getConstraintViolations().stream().map(this::field).toList();
        } else {
            errors = List.of();
        }
        return handle(new LearningTaskException(400, "TASK_REQUEST_INVALID", "请求参数格式不正确", false,
            errors, null, null));
    }

    private LearningTaskErrorVo.FieldErrorVo field(ConstraintViolation<?> violation) {
        String field = "request";
        for (var node : violation.getPropertyPath()) {
            if (node.getName() != null) field = node.getName();
        }
        var error = ApiFieldErrors.from(field,
            violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(), violation.getMessage());
        return new LearningTaskErrorVo.FieldErrorVo(error.field(), error.code(), error.message());
    }

    private List<LearningTaskErrorVo.FieldErrorVo> fields(List<org.dromara.certmuse.shared.web.ApiFieldError> errors) {
        return errors.stream().map(error -> new LearningTaskErrorVo.FieldErrorVo(
            error.field(), error.code(), error.message())).toList();
    }
}
