package org.dromara.certmuse.assessment.controller;

import java.util.List;
import org.dromara.certmuse.assessment.domain.vo.PracticeAiErrorVo;
import org.dromara.certmuse.assessment.support.PracticeAiException;
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
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps learner AI chat failures to its stable ordinary JSON envelope. */
@RestControllerAdvice(assignableTypes = PracticeAiChatController.class)
@Order(CertMuseAdviceOrder.DOMAIN)
public class PracticeAiChatExceptionHandler {
    @ExceptionHandler(PracticeAiException.class)
    public ResponseEntity<R<PracticeAiErrorVo>> handle(PracticeAiException exception) {
        return CertMuseErrorResponses.fail(exception, traceId -> new PracticeAiErrorVo(
            exception.errorCode(), exception.retryable(), traceId,
            exception.apiFieldErrors().stream().map(this::field).toList(),
            (PracticeAiErrorVo.DetailsVo) exception.details()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
        MissingRequestHeaderException.class, HttpMessageNotReadableException.class,
        HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<R<PracticeAiErrorVo>> invalid(Exception exception) {
        List<ApiFieldError> fields;
        if (exception instanceof MethodArgumentNotValidException method) {
            fields = ApiFieldErrors.from(method.getBindingResult().getFieldErrors());
        } else if (exception instanceof BindException bind) {
            fields = ApiFieldErrors.from(bind.getFieldErrors());
        } else if (exception instanceof MissingRequestHeaderException header) {
            fields = List.of(new ApiFieldError(header.getHeaderName(), "REQUIRED", "请求头不能为空"));
        } else if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            fields = List.of(new ApiFieldError(mismatch.getName(), "INVALID_FORMAT", "请求参数格式不正确"));
        } else {
            fields = List.of();
        }
        return handle(new PracticeAiException(400, "AI_CHAT_REQUEST_INVALID", "请求参数不正确", false,
            fields.stream().map(this::field).toList(), null, exception));
    }

    private PracticeAiErrorVo.FieldErrorVo field(ApiFieldError field) {
        return new PracticeAiErrorVo.FieldErrorVo(field.field(), field.code(), field.message());
    }
}
