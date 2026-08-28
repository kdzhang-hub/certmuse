package org.dromara.certmuse.catalog.controller;

import org.dromara.certmuse.catalog.support.TextbookPdfException;
import org.dromara.certmuse.learning.controller.LearningTextbookPdfController;
import org.dromara.certmuse.shared.web.CertMuseAdviceOrder;
import org.dromara.certmuse.shared.web.CertMuseApiError;
import org.dromara.certmuse.shared.web.CertMuseErrorResponses;
import org.dromara.common.core.domain.R;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Error boundary for the independent textbook-PDF APIs. */
@Order(CertMuseAdviceOrder.DOMAIN)
@RestControllerAdvice(assignableTypes = {TextbookPdfAdminController.class, LearningTextbookPdfController.class})
public class TextbookPdfExceptionHandler {
    @ExceptionHandler(TextbookPdfException.class)
    public ResponseEntity<R<CertMuseApiError>> handle(TextbookPdfException exception) {
        return CertMuseErrorResponses.fail(exception);
    }
}
