package org.dromara.certmuse.learning.controller;

import java.util.List;
import org.dromara.certmuse.learning.domain.vo.ContractErrorVo;
import org.dromara.certmuse.learning.support.LearningGoalException;
import org.dromara.certmuse.shared.web.ApiFieldErrors;
import org.dromara.certmuse.shared.web.CertMuseErrorResponses;
import org.dromara.certmuse.shared.web.CertMuseAdviceOrder;
import org.dromara.common.core.domain.R;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Stabilizes the U03 error envelope and hides persistence details. */
@Order(CertMuseAdviceOrder.DOMAIN)
@RestControllerAdvice(assignableTypes = LearningGoalController.class)
public class LearningGoalExceptionHandler {
    @ExceptionHandler(LearningGoalException.class)
    public ResponseEntity<R<ContractErrorVo>> handle(LearningGoalException exception) {
        ContractErrorVo data = exception.data();
        return CertMuseErrorResponses.fail(exception, traceId -> new ContractErrorVo(
            data.errorCode(), data.retryable(), traceId, data.fieldErrors()));
    }

    /** Maps request binding failures to the frozen U03 validation envelope. */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
        HttpMessageNotReadableException.class})
    public ResponseEntity<R<ContractErrorVo>> handleInvalidRequest(Exception exception) {
        List<ContractErrorVo.FieldErrorVo> fieldErrors;
        if (exception instanceof MethodArgumentNotValidException method) {
            fieldErrors = ApiFieldErrors.from(method.getBindingResult().getFieldErrors()).stream()
                .map(error -> new ContractErrorVo.FieldErrorVo(error.field(), error.code(), error.message()))
                .toList();
        } else if (exception instanceof BindException bind) {
            fieldErrors = ApiFieldErrors.from(bind.getFieldErrors()).stream()
                .map(error -> new ContractErrorVo.FieldErrorVo(error.field(), error.code(), error.message()))
                .toList();
        } else {
            fieldErrors = List.of();
        }
        return handle(new LearningGoalException(
            400, "GOAL_REQUEST_INVALID", "请求参数格式不正确", false, null, fieldErrors));
    }

}
