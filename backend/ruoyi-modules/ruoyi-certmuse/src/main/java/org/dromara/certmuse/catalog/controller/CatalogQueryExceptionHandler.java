package org.dromara.certmuse.catalog.controller;

import org.dromara.certmuse.catalog.domain.vo.CatalogQueryErrorVo;
import org.dromara.certmuse.catalog.support.CatalogQueryException;
import org.dromara.certmuse.shared.web.CertMuseErrorResponses;
import org.dromara.certmuse.shared.web.CertMuseAdviceOrder;
import org.dromara.common.core.domain.R;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(CertMuseAdviceOrder.DOMAIN)
@RestControllerAdvice(assignableTypes = KnowledgeTreeController.class)
public class CatalogQueryExceptionHandler {
    @ExceptionHandler(CatalogQueryException.class)
    public ResponseEntity<R<CatalogQueryErrorVo>> handle(CatalogQueryException exception) {
        return CertMuseErrorResponses.fail(exception,
            traceId -> new CatalogQueryErrorVo(exception.errorCode(), traceId));
    }
}
