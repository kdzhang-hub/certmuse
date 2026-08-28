package org.dromara.certmuse.learning.controller;

import java.util.List;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.dromara.certmuse.learning.domain.vo.HistoryErrorVo;
import org.dromara.certmuse.learning.support.LearningHistoryException;
import org.dromara.certmuse.shared.web.ApiFieldError;
import org.dromara.certmuse.shared.web.ApiFieldErrors;
import org.dromara.certmuse.shared.web.CertMuseAdviceOrder;
import org.dromara.certmuse.shared.web.CertMuseErrorResponses;
import org.dromara.common.core.domain.R;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps learning-history failures to the frozen V1 envelope. */
@Order(CertMuseAdviceOrder.DOMAIN)
@RestControllerAdvice(assignableTypes = LearningHistoryController.class)
public class LearningHistoryExceptionHandler {
    @ExceptionHandler(LearningHistoryException.class)
    public ResponseEntity<R<HistoryErrorVo>> handle(LearningHistoryException exception) {
        return CertMuseErrorResponses.fail(exception, traceId -> new HistoryErrorVo(
            exception.errorCode(), exception.retryable(), traceId,
            exception.apiFieldErrors().stream().map(this::field).toList(), exception.details()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
        HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        ConstraintViolationException.class})
    public ResponseEntity<R<HistoryErrorVo>> invalid(Exception exception) {
        List<HistoryErrorVo.FieldErrorVo> errors = exception instanceof MethodArgumentNotValidException method
            ? ApiFieldErrors.from(method.getBindingResult().getFieldErrors()).stream().map(this::field).toList()
            : exception instanceof BindException bind
                ? ApiFieldErrors.from(bind.getFieldErrors()).stream().map(this::field).toList()
                : exception instanceof MethodArgumentTypeMismatchException mismatch
                    ? List.of(new HistoryErrorVo.FieldErrorVo(mismatch.getName(), "INVALID_FORMAT", "请求参数格式不正确"))
                    : exception instanceof ConstraintViolationException violations
                        ? violations.getConstraintViolations().stream().map(this::field).toList() : List.of();
        return handle(new LearningHistoryException(400, "HISTORY_REQUEST_INVALID", "请求参数格式不正确",
            false, errors, null, null));
    }

    private HistoryErrorVo.FieldErrorVo field(ApiFieldError error) {
        return new HistoryErrorVo.FieldErrorVo(error.field(), error.code(), error.message());
    }

    private HistoryErrorVo.FieldErrorVo field(ConstraintViolation<?> violation) {
        String name = "request";
        for (var node : violation.getPropertyPath()) if (node.getName() != null) name = node.getName();
        return field(ApiFieldErrors.from(name,
            violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
            violation.getMessage()));
    }
}
