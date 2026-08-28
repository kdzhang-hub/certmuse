package org.dromara.certmuse.assessment.controller;

import java.util.List;
import org.dromara.certmuse.assessment.domain.vo.KnowledgePracticeErrorVo;
import org.dromara.certmuse.assessment.support.KnowledgePracticeException;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps U08 failures to its stable error envelope. */
@RestControllerAdvice(assignableTypes = KnowledgePracticeController.class)
@Order(CertMuseAdviceOrder.DOMAIN)
public class KnowledgePracticeExceptionHandler {
    @ExceptionHandler(KnowledgePracticeException.class)
    public ResponseEntity<R<KnowledgePracticeErrorVo>> handle(KnowledgePracticeException exception) {
        return CertMuseErrorResponses.fail(exception, traceId -> new KnowledgePracticeErrorVo(
            exception.errorCode(), exception.retryable(), traceId,
            exception.apiFieldErrors().stream().map(this::field).toList(),
            (KnowledgePracticeErrorVo.DetailsVo) exception.details()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
        MissingRequestHeaderException.class, HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class})
    public ResponseEntity<R<KnowledgePracticeErrorVo>> invalid(Exception exception) {
        List<KnowledgePracticeErrorVo.FieldErrorVo> fields;
        if (exception instanceof MethodArgumentNotValidException method) {
            fields = ApiFieldErrors.from(method.getBindingResult().getFieldErrors()).stream().map(this::field).toList();
        } else if (exception instanceof BindException bind) {
            fields = ApiFieldErrors.from(bind.getFieldErrors()).stream().map(this::field).toList();
        } else if (exception instanceof MissingRequestHeaderException header) {
            fields = List.of(new KnowledgePracticeErrorVo.FieldErrorVo(
                header.getHeaderName(), "REQUIRED", "请求头不能为空"));
        } else if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            fields = List.of(new KnowledgePracticeErrorVo.FieldErrorVo(
                mismatch.getName(), "INVALID_FORMAT", "必须是十进制正整数"));
        } else {
            fields = List.of(new KnowledgePracticeErrorVo.FieldErrorVo("body", "INVALID_FORMAT", "请求体不是有效JSON"));
        }
        return handle(new KnowledgePracticeException(400, "PRACTICE_REQUEST_INVALID", "请求参数格式不正确",
            false, fields, null, null));
    }

    private KnowledgePracticeErrorVo.FieldErrorVo field(ApiFieldError field) {
        return new KnowledgePracticeErrorVo.FieldErrorVo(field.field(), field.code(), field.message());
    }
}
