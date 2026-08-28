package org.dromara.certmuse.catalog.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.catalog.service.TextbookPdfService;
import org.dromara.certmuse.catalog.support.TextbookPdfByteRange;
import org.dromara.certmuse.catalog.support.TextbookPdfReaderTicket;
import org.dromara.certmuse.catalog.support.TextbookPdfStreamException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/** Same-origin browser stream for an anonymous published-textbook reader ticket. */
@SaIgnore
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/reader/textbook-pdfs")
public class PublicTextbookPdfReaderController {
    private final TextbookPdfService service;

    @GetMapping(value = "/{ticket}", produces = MediaType.APPLICATION_PDF_VALUE)
    public void get(@PathVariable String ticket,
                    @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                    HttpServletResponse response) throws IOException {
        write(ticket, range, response, false);
    }

    @RequestMapping(value = "/{ticket}", method = RequestMethod.HEAD, produces = MediaType.APPLICATION_PDF_VALUE)
    public void head(@PathVariable String ticket,
                     @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                     HttpServletResponse response) throws IOException {
        write(ticket, range, response, true);
    }

    private void write(String value, String rangeHeader, HttpServletResponse response, boolean head) throws IOException {
        TextbookPdfReaderTicket ticket = service.resolvePublicReaderTicket(value);
        TextbookPdfByteRange range;
        try {
            range = TextbookPdfByteRange.parse(rangeHeader, ticket.fileSize());
        } catch (TextbookPdfStreamException exception) {
            response.setStatus(exception.status());
            if (exception.status() == HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE) {
                response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + ticket.fileSize());
                return;
            }
            throw exception;
        }
        boolean partial = rangeHeader != null && !rangeHeader.isBlank();
        response.setStatus(partial ? HttpServletResponse.SC_PARTIAL_CONTENT : HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentLengthLong(range.length());
        if (partial) {
            response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + range.start() + '-' + range.end() + '/' + ticket.fileSize());
        }
        if (!head) {
            service.streamReaderTicket(ticket, partial ? range.value() : null, response.getOutputStream());
        }
    }
}
