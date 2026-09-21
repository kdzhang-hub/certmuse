package org.dromara.certmuse.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.OutputStream;
import org.dromara.certmuse.catalog.service.TextbookPdfService;
import org.dromara.certmuse.catalog.support.TextbookPdfReaderTicket;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("dev")
class TextbookPdfReaderControllerTest {

    @Test
    void streamsSingleRangeWithSafeInlineHeaders() throws Exception {
        TextbookPdfService service = mock(TextbookPdfService.class);
        TextbookPdfReaderTicket ticket = new TextbookPdfReaderTicket("private-key", "book.pdf", 10);
        when(service.resolveReaderTicket("opaque")).thenReturn(ticket);
        doAnswer(invocation -> {
            ((OutputStream) invocation.getArgument(2)).write(new byte[] {2, 3, 4, 5});
            return null;
        }).when(service).streamReaderTicket(any(), any(), any());
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TextbookPdfReaderController(service).get("opaque", "bytes=2-5", response);

        assertThat(response.getStatus()).isEqualTo(206);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("inline");
        assertThat(response.getHeader("Accept-Ranges")).isEqualTo("bytes");
        assertThat(response.getHeader("Content-Range")).isEqualTo("bytes 2-5/10");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getContentAsByteArray()).containsExactly(2, 3, 4, 5);
        verify(service).streamReaderTicket(ticket, "bytes=2-5", response.getOutputStream());
    }

    @Test
    void streamsTheWholeObjectWhenTheBrowserDoesNotSendRange() throws Exception {
        TextbookPdfService service = mock(TextbookPdfService.class);
        TextbookPdfReaderTicket ticket = new TextbookPdfReaderTicket("private-key", "book.pdf", 3);
        when(service.resolveReaderTicket("opaque")).thenReturn(ticket);
        doAnswer(invocation -> {
            ((OutputStream) invocation.getArgument(2)).write(new byte[] {1, 2, 3});
            return null;
        }).when(service).streamReaderTicket(any(), any(), any());
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TextbookPdfReaderController(service).get("opaque", null, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Content-Range")).isNull();
        assertThat(response.getContentAsByteArray()).containsExactly(1, 2, 3);
        verify(service).streamReaderTicket(ticket, null, response.getOutputStream());
    }

    @Test
    void headDoesNotStreamAndInvalidRangeReturns416() throws Exception {
        TextbookPdfService service = mock(TextbookPdfService.class);
        TextbookPdfReaderTicket ticket = new TextbookPdfReaderTicket("private-key", "book.pdf", 10);
        when(service.resolveReaderTicket("opaque")).thenReturn(ticket);
        TextbookPdfReaderController controller = new TextbookPdfReaderController(service);
        MockHttpServletResponse head = new MockHttpServletResponse();
        MockHttpServletResponse invalid = new MockHttpServletResponse();

        controller.head("opaque", null, head);
        controller.get("opaque", "bytes=10-", invalid);

        assertThat(head.getStatus()).isEqualTo(200);
        assertThat(head.getContentAsByteArray()).isEmpty();
        assertThat(invalid.getStatus()).isEqualTo(416);
        assertThat(invalid.getHeader("Content-Range")).isEqualTo("bytes */10");
        verify(service, never()).streamReaderTicket(any(), any(), any());
    }
}
