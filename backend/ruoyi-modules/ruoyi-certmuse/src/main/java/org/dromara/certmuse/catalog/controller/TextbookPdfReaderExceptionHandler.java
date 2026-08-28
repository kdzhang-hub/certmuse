package org.dromara.certmuse.catalog.controller;

import org.dromara.certmuse.catalog.support.TextbookPdfStreamException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keeps ticket and private-storage failures out of binary PDF responses. */
@RestControllerAdvice(assignableTypes = {TextbookPdfReaderController.class, PublicTextbookPdfReaderController.class})
public class TextbookPdfReaderExceptionHandler {
    @ExceptionHandler(TextbookPdfStreamException.class)
    public ResponseEntity<Void> handle(TextbookPdfStreamException exception) {
        return ResponseEntity.status(exception.status()).build();
    }
}
