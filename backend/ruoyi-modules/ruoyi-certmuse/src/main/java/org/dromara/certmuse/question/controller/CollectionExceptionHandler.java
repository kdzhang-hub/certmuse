package org.dromara.certmuse.question.controller;

import org.dromara.certmuse.question.domain.vo.CollectionErrorVo;
import org.dromara.certmuse.question.support.CollectionException;
import org.dromara.certmuse.shared.web.CertMuseErrorResponses;
import org.dromara.certmuse.shared.web.CertMuseAdviceOrder;
import org.dromara.common.core.domain.R;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(CertMuseAdviceOrder.DOMAIN)
@RestControllerAdvice(assignableTypes = CollectionController.class)
public class CollectionExceptionHandler {
    @ExceptionHandler(CollectionException.class)
    public ResponseEntity<R<CollectionErrorVo>> handle(CollectionException exception) {
        CollectionErrorVo data = exception.getData();
        return CertMuseErrorResponses.fail(exception, traceId -> new CollectionErrorVo(
            data.errorCode(), data.currentRevisionId(), data.currentRowVersion(),
            data.workingRevisionId(), traceId, data.blockingIssues()));
    }

}
