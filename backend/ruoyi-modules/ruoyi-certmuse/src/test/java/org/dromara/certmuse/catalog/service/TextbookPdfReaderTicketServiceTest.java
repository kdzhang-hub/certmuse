package org.dromara.certmuse.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.dromara.certmuse.catalog.domain.TextbookPdfRow;
import org.dromara.certmuse.catalog.mapper.TextbookPdfMapper;
import org.dromara.certmuse.catalog.service.impl.ImportPersistenceService;
import org.dromara.certmuse.catalog.service.impl.TextbookPdfServiceImpl;
import org.dromara.certmuse.catalog.support.TextbookPdfReaderTicket;
import org.dromara.certmuse.catalog.support.TextbookPdfReaderTicketStore;
import org.dromara.certmuse.catalog.support.TextbookPdfStreamException;
import org.dromara.certmuse.catalog.support.TextbookPdfStorage;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.transaction.PlatformTransactionManager;

@Tag("dev")
class TextbookPdfReaderTicketServiceTest {

    @Test
    void hidesExpiredReaderTicketsAsNotFound() {
        TextbookPdfReaderTicketStore tickets = mock(TextbookPdfReaderTicketStore.class);
        TextbookPdfService service = new TextbookPdfServiceImpl(mock(TextbookPdfMapper.class), mock(TextbookPdfStorage.class), tickets,
            mock(ImportPersistenceService.class), mock(PlatformTransactionManager.class));
        when(tickets.find("expired")).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.resolveReaderTicket("expired"))
            .isInstanceOfSatisfying(TextbookPdfStreamException.class, exception -> assertThat(exception.status()).isEqualTo(404));
    }

    @Test
    void returnsOnlyAnOpaqueSameOriginReaderPathAfterAuthorization() {
        TextbookPdfMapper mapper = mock(TextbookPdfMapper.class);
        TextbookPdfReaderTicketStore tickets = mock(TextbookPdfReaderTicketStore.class);
        TextbookPdfService service = new TextbookPdfServiceImpl(mapper, mock(TextbookPdfStorage.class), tickets,
            mock(ImportPersistenceService.class), mock(PlatformTransactionManager.class));
        TextbookPdfRow row = new TextbookPdfRow(1L, "教材", "draft", "textbooks/1/original/private.pdf",
            "book.pdf", 10L, "hash", 7L, OffsetDateTime.now());
        when(mapper.selectAdmin(1L, null)).thenReturn(row);
        when(tickets.issue(new TextbookPdfReaderTicket(row.objectKey(), row.fileName(), row.fileSize()), java.time.Duration.ofMinutes(60)))
            .thenReturn("opaque-ticket");

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(true);
            var reader = service.adminReader("1");

            assertThat(reader.readUrl()).isEqualTo("/api/reader/textbook-pdfs/opaque-ticket");
            assertThat(reader.readUrl()).doesNotContain("minio", "private.pdf", "X-Amz");
        }
        verify(mapper).selectAdmin(1L, null);
    }

    @Test
    void publicReaderIssuesASeparatePublicOnlyTicketForPublishedTextbooks() {
        TextbookPdfMapper mapper = mock(TextbookPdfMapper.class);
        TextbookPdfReaderTicketStore tickets = mock(TextbookPdfReaderTicketStore.class);
        TextbookPdfService service = new TextbookPdfServiceImpl(mapper, mock(TextbookPdfStorage.class), tickets,
            mock(ImportPersistenceService.class), mock(PlatformTransactionManager.class));
        TextbookPdfRow row = new TextbookPdfRow(1L, "教材", "published", "textbooks/1/original/private.pdf",
            "book.pdf", 10L, "hash", 7L, OffsetDateTime.now());
        when(mapper.selectPublic(1L)).thenReturn(row);
        when(tickets.issue(any(), eq(java.time.Duration.ofMinutes(60)))).thenReturn("public-ticket");

        var reader = service.publicReader("1");

        ArgumentCaptor<TextbookPdfReaderTicket> ticket = ArgumentCaptor.forClass(TextbookPdfReaderTicket.class);
        verify(tickets).issue(ticket.capture(), eq(java.time.Duration.ofMinutes(60)));
        assertThat(ticket.getValue().isPublic()).isTrue();
        assertThat(reader.readUrl()).isEqualTo("/api/public/reader/textbook-pdfs/public-ticket");
    }

    @Test
    void publicReaderPathRejectsAnAuthenticatedReaderTicket() {
        TextbookPdfReaderTicketStore tickets = mock(TextbookPdfReaderTicketStore.class);
        TextbookPdfService service = new TextbookPdfServiceImpl(mock(TextbookPdfMapper.class), mock(TextbookPdfStorage.class), tickets,
            mock(ImportPersistenceService.class), mock(PlatformTransactionManager.class));
        when(tickets.find("private-ticket")).thenReturn(Optional.of(new TextbookPdfReaderTicket("private-key", "book.pdf", 10)));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.resolvePublicReaderTicket("private-ticket"))
            .isInstanceOfSatisfying(TextbookPdfStreamException.class, exception -> assertThat(exception.status()).isEqualTo(404));
    }
}
