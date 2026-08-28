package org.dromara.certmuse.question.controller;

import org.dromara.certmuse.question.domain.vo.QuestionErrorVo;
import org.dromara.certmuse.question.support.QuestionException;
import org.dromara.certmuse.shared.web.CertMuseErrorResponses;
import org.dromara.certmuse.shared.web.CertMuseAdviceOrder;
import org.dromara.common.core.domain.R;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(CertMuseAdviceOrder.DOMAIN)
@RestControllerAdvice(assignableTypes = {QuestionController.class, QuestionReviewController.class})
public class QuestionExceptionHandler {
    @ExceptionHandler(QuestionException.class)
    public ResponseEntity<R<QuestionErrorVo>> handle(QuestionException exception) {
        QuestionErrorVo data = exception.getData();
        return CertMuseErrorResponses.fail(exception, traceId -> new QuestionErrorVo(
            data.errorCode(), data.currentRevisionId(), data.currentRowVersion(),
            data.workingRevisionId(), traceId, data.blockingIssues()));
    }

}
