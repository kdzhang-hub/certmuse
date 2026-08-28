package org.dromara.certmuse.catalog.controller;

import java.util.List;

import org.dromara.certmuse.catalog.support.SyllabusPublishedDateException;
import org.dromara.certmuse.shared.web.ApiFieldError;
import org.dromara.certmuse.shared.web.ApiFieldErrors;
import org.dromara.certmuse.shared.web.CertMuseApiError;
import org.dromara.certmuse.shared.web.CertMuseErrorResponses;
import org.dromara.common.core.domain.R;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keeps the published-date update response aligned with its dedicated API contract. */
@org.springframework.core.annotation.Order(99)
@RestControllerAdvice(assignableTypes = SyllabusPublishedDateController.class)
public class SyllabusPublishedDateExceptionHandler {
    @ExceptionHandler(SyllabusPublishedDateException.class)
    public ResponseEntity<R<CertMuseApiError>> handle(SyllabusPublishedDateException exception) {
        return CertMuseErrorResponses.fail(exception);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
        MissingRequestHeaderException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<R<CertMuseApiError>> handleInvalidRequest(Exception exception) {
        List<ApiFieldError> fieldErrors = exception instanceof MethodArgumentNotValidException method
            ? ApiFieldErrors.from(method.getBindingResult().getFieldErrors())
            : exception instanceof BindException bind
            ? ApiFieldErrors.from(bind.getFieldErrors())
            : exception instanceof MissingRequestHeaderException
            ? List.of(new ApiFieldError("requestId", "REQUIRED", "X-Request-Id不能为空"))
            : List.of(new ApiFieldError("publishedDate", "INVALID_FORMAT", "发布日期格式不正确"));
        return handle(new SyllabusPublishedDateException(
            400, "SYLLABUS_PUBLISHED_DATE_INVALID", "请求参数校验失败", false, fieldErrors, null, null));
    }
}
