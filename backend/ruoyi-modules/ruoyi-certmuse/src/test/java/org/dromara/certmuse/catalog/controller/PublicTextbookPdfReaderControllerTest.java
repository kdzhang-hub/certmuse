package org.dromara.certmuse.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.OutputStream;
import org.dromara.certmuse.catalog.service.TextbookPdfService;
import org.dromara.certmuse.catalog.support.TextbookPdfReaderTicket;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("dev")
class PublicTextbookPdfReaderControllerTest {

    @Test
    void streamsOnlyTheTicketResolvedByThePublicReaderBoundary() throws Exception {
        TextbookPdfService service = mock(TextbookPdfService.class);
        TextbookPdfReaderTicket ticket = new TextbookPdfReaderTicket("private-key", "book.pdf", 4,
            TextbookPdfReaderTicket.PUBLIC_AUDIENCE);
        when(service.resolvePublicReaderTicket("public-ticket")).thenReturn(ticket);
        doAnswer(invocation -> {
            ((OutputStream) invocation.getArgument(2)).write(new byte[] {1, 2, 3, 4});
            return null;
        }).when(service).streamReaderTicket(any(), any(), any());
        MockHttpServletResponse response = new MockHttpServletResponse();

        new PublicTextbookPdfReaderController(service).get("public-ticket", null, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");
        assertThat(response.getContentAsByteArray()).containsExactly(1, 2, 3, 4);
        verify(service).resolvePublicReaderTicket("public-ticket");
    }
}
