package org.dromara.certmuse.assessment.controller;

import java.util.List;
import org.dromara.certmuse.assessment.domain.vo.SimulationErrorVo;
import org.dromara.certmuse.assessment.support.SimulationException;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps U13 failures to its stable response envelope. */
@RestControllerAdvice(assignableTypes = SimulationController.class)
@Order(CertMuseAdviceOrder.DOMAIN)
public class SimulationExceptionHandler {
    @ExceptionHandler(SimulationException.class)
    public ResponseEntity<R<SimulationErrorVo>> handle(SimulationException exception) {
        if (exception.status() == 500) {
            return CertMuseErrorResponses.fail(exception, traceId -> body(exception, traceId));
        }
        return CertMuseErrorResponses.fail(exception.status(), exception.getMessage(), body(exception, null));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
        MissingRequestHeaderException.class, HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class})
    public ResponseEntity<R<SimulationErrorVo>> invalid(Exception exception) {
        List<SimulationErrorVo.FieldErrorVo> fields;
        if (exception instanceof MethodArgumentNotValidException method) {
            fields = ApiFieldErrors.from(method.getBindingResult().getFieldErrors()).stream()
                .map(this::field).toList();
        } else if (exception instanceof BindException bind) {
            fields = ApiFieldErrors.from(bind.getFieldErrors()).stream().map(this::field).toList();
        } else if (exception instanceof MissingRequestHeaderException header) {
            fields = List.of(new SimulationErrorVo.FieldErrorVo(
                header.getHeaderName(), "REQUIRED", "请求头不能为空"));
        } else if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            fields = List.of(new SimulationErrorVo.FieldErrorVo(
                mismatch.getName(), "INVALID_FORMAT", "请求参数格式不正确"));
        } else {
            fields = List.of(new SimulationErrorVo.FieldErrorVo("body", "INVALID_FORMAT", "请求体不是有效JSON"));
        }
        return handle(new SimulationException(400, "SIMULATION_REQUEST_INVALID", "请求参数格式不正确",
            false, fields, null, null));
    }

    private SimulationErrorVo.FieldErrorVo field(ApiFieldError field) {
        return new SimulationErrorVo.FieldErrorVo(field.field(), field.code(), field.message());
    }

    private SimulationErrorVo body(SimulationException exception, String traceId) {
        return new SimulationErrorVo(exception.errorCode(), exception.retryable(), traceId,
            exception.apiFieldErrors().stream().map(this::field).toList(),
            (SimulationErrorVo.DetailsVo) exception.details());
    }
}
