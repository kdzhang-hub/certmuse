package org.dromara.certmuse.assessment.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.dromara.certmuse.assessment.domain.vo.PastPaperErrorVo;
import org.dromara.certmuse.assessment.support.PastPaperException;
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
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps U15 expected failures without leaking stored question snapshots. */
@RestControllerAdvice(assignableTypes = PastPaperController.class)
@Order(CertMuseAdviceOrder.DOMAIN)
public class PastPaperExceptionHandler {
    @ExceptionHandler(PastPaperException.class)
    public ResponseEntity<R<PastPaperErrorVo>> handle(PastPaperException exception) {
        return CertMuseErrorResponses.fail(exception, traceId -> new PastPaperErrorVo(exception.errorCode(), exception.retryable(), traceId,
            exception.apiFieldErrors().stream().map(this::field).toList(), exception.details()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, MissingRequestHeaderException.class,
        HttpMessageNotReadableException.class})
    public ResponseEntity<R<PastPaperErrorVo>> invalid(Exception exception, HttpServletRequest request) {
        List<PastPaperErrorVo.FieldErrorVo> fields = exception instanceof MethodArgumentNotValidException method
            ? ApiFieldErrors.from(method.getBindingResult().getFieldErrors()).stream().map(this::field).toList()
            : exception instanceof BindException bind ? ApiFieldErrors.from(bind.getFieldErrors()).stream().map(this::field).toList()
            : exception instanceof MissingRequestHeaderException missing
                ? List.of(new PastPaperErrorVo.FieldErrorVo(missing.getHeaderName(), "REQUIRED", "请求头不能为空"))
                : List.of(new PastPaperErrorVo.FieldErrorVo("body", "INVALID_FORMAT", "请求参数格式不正确"));
        String errorCode = request.getRequestURI().contains("practice-sessions")
            ? "PAST_PAPER_PRACTICE_REQUEST_INVALID" : "PAST_PAPER_REQUEST_INVALID";
        return handle(new PastPaperException(400, errorCode, "请求参数格式不正确", false, fields, null));
    }
    private PastPaperErrorVo.FieldErrorVo field(ApiFieldError field) {
        return new PastPaperErrorVo.FieldErrorVo(field.field(), field.code(), field.message());
    }
}
