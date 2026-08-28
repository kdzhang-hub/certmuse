package org.dromara.certmuse.catalog.controller;

import java.util.List;
import org.dromara.certmuse.catalog.domain.vo.QualificationVersionErrorVo;
import org.dromara.certmuse.catalog.domain.vo.QualificationVersionFieldErrorVo;
import org.dromara.certmuse.catalog.support.QualificationVersionException;
import org.dromara.certmuse.shared.web.ApiFieldErrors;
import org.dromara.certmuse.shared.web.CertMuseErrorResponses;
import org.dromara.certmuse.shared.web.CertMuseAdviceOrder;
import org.dromara.common.core.domain.R;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingRequestHeaderException;

/** Keeps M01 errors stable and prevents persistence details leaking from the API. */
@Order(CertMuseAdviceOrder.DOMAIN)
@RestControllerAdvice(assignableTypes = QualificationVersionController.class)
public class QualificationVersionExceptionHandler {
    @ExceptionHandler(QualificationVersionException.class)
    public ResponseEntity<R<QualificationVersionErrorVo>> handle(QualificationVersionException exception) {
        QualificationVersionErrorVo data = exception.data();
        return CertMuseErrorResponses.fail(exception, traceId -> new QualificationVersionErrorVo(
            data.errorCode(), data.fieldErrors(), data.blockers(), traceId));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, MissingRequestHeaderException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<R<QualificationVersionErrorVo>> handleInvalidRequest(Exception exception) {
        List<QualificationVersionFieldErrorVo> fields = exception instanceof MethodArgumentNotValidException method
            ? ApiFieldErrors.from(method.getBindingResult().getFieldErrors()).stream()
                .map(error -> new QualificationVersionFieldErrorVo(error.field(), error.message())).toList()
            : exception instanceof BindException bind
            ? ApiFieldErrors.from(bind.getFieldErrors()).stream()
                .map(error -> new QualificationVersionFieldErrorVo(error.field(), error.message())).toList()
            : exception instanceof MissingRequestHeaderException header
            ? List.of(new QualificationVersionFieldErrorVo(header.getHeaderName(), "请求头不能为空"))
            : List.of(new QualificationVersionFieldErrorVo("request", "请求体格式不正确"));
        return handle(new QualificationVersionException(400, "QUALIFICATION_VERSION_INVALID", "请求参数校验失败", fields, List.of(), null));
    }

}
