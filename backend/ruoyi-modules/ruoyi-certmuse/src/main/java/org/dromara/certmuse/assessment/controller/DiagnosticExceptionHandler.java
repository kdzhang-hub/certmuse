package org.dromara.certmuse.assessment.controller;

import java.util.List;
import org.dromara.certmuse.assessment.domain.vo.DiagnosticErrorVo;
import org.dromara.certmuse.assessment.support.DiagnosticException;
import org.dromara.certmuse.shared.web.ApiFieldError;
import org.dromara.certmuse.shared.web.ApiFieldErrors;
import org.dromara.certmuse.shared.web.CertMuseErrorResponses;
import org.dromara.certmuse.shared.web.CertMuseAdviceOrder;
import org.dromara.common.core.domain.R;
import org.springframework.http.ResponseEntity;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps only diagnostic domain failures to the frozen diagnostic error contract. */
@RestControllerAdvice(assignableTypes = DiagnosticController.class)
@Order(CertMuseAdviceOrder.DOMAIN)
public class DiagnosticExceptionHandler {
    @ExceptionHandler(DiagnosticException.class)
    public ResponseEntity<R<DiagnosticErrorVo>> handle(DiagnosticException exception) {
        DiagnosticErrorVo data = exception.getData();
        return CertMuseErrorResponses.fail(exception, traceId -> new DiagnosticErrorVo(
            data.errorCode(), data.retryable(), traceId, data.nextAction(), data.fieldErrors()));
    }

    /** Maps request failures to the frozen diagnostic validation envelope. */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
        MissingRequestHeaderException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<R<DiagnosticErrorVo>> handleInvalidRequest(Exception exception) {
        List<DiagnosticErrorVo.FieldErrorVo> fields;
        if (exception instanceof MethodArgumentNotValidException method) {
            fields = toDiagnosticFields(ApiFieldErrors.from(method.getBindingResult().getFieldErrors()));
        } else if (exception instanceof BindException bind) {
            fields = toDiagnosticFields(ApiFieldErrors.from(bind.getFieldErrors()));
        } else if (exception instanceof MissingRequestHeaderException header) {
            fields = List.of(new DiagnosticErrorVo.FieldErrorVo(
                header.getHeaderName(), "REQUIRED", "请求头不能为空"));
        } else {
            fields = List.of();
        }
        return invalid(fields);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<R<DiagnosticErrorVo>> handleTypeMismatch(
        MethodArgumentTypeMismatchException exception
    ) {
        return invalid(List.of(new DiagnosticErrorVo.FieldErrorVo(
            exception.getName(), "INVALID_FORMAT", "请求参数格式不正确")));
    }

    private ResponseEntity<R<DiagnosticErrorVo>> invalid(
        List<DiagnosticErrorVo.FieldErrorVo> fields
    ) {
        return handle(new DiagnosticException(
            400, "DIAGNOSTIC_REQUEST_INVALID", "请求参数格式不正确", false, null, fields));
    }

    private static List<DiagnosticErrorVo.FieldErrorVo> toDiagnosticFields(
        List<ApiFieldError> fields
    ) {
        return fields.stream()
            .map(field -> new DiagnosticErrorVo.FieldErrorVo(
                field.field(), field.code(), field.message()))
            .toList();
    }
}
