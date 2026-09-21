package org.dromara.certmuse.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.dromara.certmuse.catalog.support.TextbookPdfStreamException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class TextbookPdfReaderExceptionHandlerTest {

    @Test
    void mapsExpiredTicketsAndStorageFailuresWithoutAnErrorBody() {
        TextbookPdfReaderExceptionHandler handler = new TextbookPdfReaderExceptionHandler();

        assertThat(handler.handle(TextbookPdfStreamException.unavailable()).getStatusCode().value()).isEqualTo(404);
        assertThat(handler.handle(TextbookPdfStreamException.storageUnavailable(new IllegalStateException())).getStatusCode().value()).isEqualTo(503);
    }
}
