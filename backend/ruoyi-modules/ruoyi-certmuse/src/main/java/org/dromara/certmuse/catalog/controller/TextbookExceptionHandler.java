package org.dromara.certmuse.catalog.controller;

import org.dromara.certmuse.catalog.domain.vo.TextbookErrorVo;
import org.dromara.certmuse.catalog.support.TextbookException;
import org.dromara.certmuse.shared.web.CertMuseErrorResponses;
import org.dromara.certmuse.shared.web.CertMuseAdviceOrder;
import org.dromara.common.core.domain.R;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(CertMuseAdviceOrder.DOMAIN)
@RestControllerAdvice(assignableTypes = TextbookController.class)
public class TextbookExceptionHandler {
    @ExceptionHandler(TextbookException.class)
    public ResponseEntity<R<TextbookErrorVo>> handle(TextbookException e) {
        return CertMuseErrorResponses.fail(e, traceId -> new TextbookErrorVo(
            e.errorCode(), e.retryable(), traceId, e.getFieldErrors()));
    }
}
