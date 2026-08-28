package org.dromara.certmuse.learning.controller;

import java.util.List;
import org.dromara.certmuse.learning.domain.vo.ContractErrorVo;
import org.dromara.certmuse.learning.domain.vo.GoalSwitchErrorVo;
import org.dromara.certmuse.learning.support.GoalSwitchException;
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

/** Keeps U12 failures independent from the frozen U03 error envelope. */
@Order(CertMuseAdviceOrder.DOMAIN)
@RestControllerAdvice(assignableTypes = LearningGoalSwitchController.class)
public class LearningGoalSwitchExceptionHandler {
    @ExceptionHandler(GoalSwitchException.class)
    public ResponseEntity<R<GoalSwitchErrorVo>> handle(GoalSwitchException exception) {
        GoalSwitchErrorVo data = exception.data();
        return CertMuseErrorResponses.fail(exception, traceId -> new GoalSwitchErrorVo(
            data.errorCode(), data.retryable(), traceId, data.fieldErrors(), data.details()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
        HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    public ResponseEntity<R<GoalSwitchErrorVo>> invalid(Exception exception) {
        List<ContractErrorVo.FieldErrorVo> fields = exception instanceof MethodArgumentNotValidException method
            ? ApiFieldErrors.from(method.getBindingResult().getFieldErrors()).stream()
                .map(error -> new ContractErrorVo.FieldErrorVo(error.field(), error.code(), error.message())).toList()
            : List.of();
        return handle(new GoalSwitchException(400, "GOAL_SWITCH_REQUEST_INVALID", "请求参数格式不正确", false,
            fields, null, exception));
    }
}
