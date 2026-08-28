package org.dromara.certmuse.catalog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.catalog.domain.TextbookPdfRow;
import org.dromara.certmuse.catalog.domain.vo.LearningTextbookPdfListItemVo;
import org.dromara.certmuse.catalog.domain.vo.PublicTextbookCertificationVo;
import org.dromara.certmuse.catalog.domain.vo.PublicTextbookPdfListItemVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookPdfInfoVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookPdfReaderVo;
import org.dromara.certmuse.catalog.mapper.TextbookPdfMapper;
import org.dromara.certmuse.catalog.service.TextbookPdfService;
import org.dromara.certmuse.catalog.support.TextbookPdfException;
import org.dromara.certmuse.catalog.support.TextbookPdfReaderTicket;
import org.dromara.certmuse.catalog.support.TextbookPdfReaderTicketStore;
import org.dromara.certmuse.catalog.support.TextbookPdfStreamException;
import org.dromara.certmuse.catalog.support.TextbookPdfStorage;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class TextbookPdfServiceImpl implements TextbookPdfService {
    private static final Duration URL_TTL = Duration.ofMinutes(60);
    private final TextbookPdfMapper mapper;
    private final TextbookPdfStorage storage;
    private final TextbookPdfReaderTicketStore readerTicketStore;
    private final ImportPersistenceService importPersistence;
    private final PlatformTransactionManager transactionManager;

    @Value("${certmuse.textbook-pdf.max-size-bytes:209715200}")
    private long maxSizeBytes;

    @Override
    public TextbookPdfInfoVo adminInfo(String textbookId) { return info(admin(textbookId)); }

    @Override
    public TextbookPdfInfoVo upload(String textbookId, MultipartFile file) {
        long documentId = id(textbookId);
        validate(file);
        String key = "textbooks/" + documentId + "/original/" + IdUtil.fastSimpleUUID() + ".pdf";
        String fileName = safeName(file.getOriginalFilename());
        String hash;
        try (InputStream input = file.getInputStream()) {
            hash = DigestUtil.sha256Hex(input);
        } catch (IOException e) {
            throw failure(503, "TEXTBOOK_PDF_STORAGE_UNAVAILABLE", "教材PDF读取失败，请稍后重试", true, e);
        }
        try (InputStream input = file.getInputStream()) {
            storage.upload(key, input, file.getSize());
        } catch (Exception e) {
            throw failure(503, "TEXTBOOK_PDF_STORAGE_UNAVAILABLE", "教材PDF存储不可用，请稍后重试", true, e);
        }
        try {
            String oldObjectKey = new TransactionTemplate(transactionManager).execute(status -> persistUploadedPdf(
                documentId, key, fileName, file.getSize(), hash, LoginHelper.getUserId()));
            enqueueObjectCleanup(documentId, oldObjectKey, "textbook_original_pdf_replace");
        } catch (RuntimeException e) {
            deleteNewObjectAfterPersistenceFailure(documentId, key);
            throw e;
        }
        return new TextbookPdfInfoVo(true, fileName, file.getSize(), hash, OffsetDateTime.now());
    }

    @Override
    public void delete(String textbookId) {
        long documentId = id(textbookId);
        String objectKey = new TransactionTemplate(transactionManager).execute(status -> deleteUploadedPdf(documentId));
        enqueueObjectCleanup(documentId, objectKey, "textbook_original_pdf_delete");
    }

    @Override
    public TextbookPdfReaderVo adminReader(String textbookId) { return reader(adminRequirePdf(textbookId)); }

    @Override
    public List<LearningTextbookPdfListItemVo> learnerList(long userId, String keyword) {
        String normalized = keyword == null ? null : keyword.trim();
        if (normalized != null && normalized.length() > 200) throw failure(400, "TEXTBOOK_PDF_QUERY_INVALID", "keyword长度不能超过200字符");
        return mapper.selectLearnerList(userId, normalized == null || normalized.isEmpty() ? null : normalized);
    }

    @Override
    public TextbookPdfReaderVo learnerReader(long userId, String textbookId) {
        TextbookPdfRow row = mapper.selectLearner(userId, id(textbookId));
        if (row == null) throw failure(404, "TEXTBOOK_PDF_NOT_FOUND", "教材原始PDF不存在或不可访问");
        return reader(row);
    }

    @Override
    public List<PublicTextbookCertificationVo> publicCertifications() {
        return mapper.selectPublicCertifications();
    }

    @Override
    public List<PublicTextbookPdfListItemVo> publicList(String certificationId, String keyword) {
        String normalized = keyword == null ? null : keyword.trim();
        if (normalized != null && normalized.length() > 200) {
            throw failure(400, "TEXTBOOK_PDF_QUERY_INVALID", "keyword长度不能超过200字符");
        }
        return mapper.selectPublicList(id(certificationId), normalized == null || normalized.isEmpty() ? null : normalized);
    }

    @Override
    public TextbookPdfReaderVo publicReader(String textbookId) {
        TextbookPdfRow row = mapper.selectPublic(id(textbookId));
        if (row == null) throw failure(404, "TEXTBOOK_PDF_NOT_FOUND", "教材原始PDF不存在或不可访问");
        return reader(row, TextbookPdfReaderTicket.PUBLIC_AUDIENCE);
    }

    @Override
    public TextbookPdfReaderTicket resolveReaderTicket(String ticket) {
        try {
            return readerTicketStore.find(ticket).orElseThrow(TextbookPdfStreamException::unavailable);
        } catch (TextbookPdfStreamException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw TextbookPdfStreamException.storageUnavailable(exception);
        }
    }

    @Override
    public TextbookPdfReaderTicket resolvePublicReaderTicket(String ticket) {
        TextbookPdfReaderTicket resolved = resolveReaderTicket(ticket);
        if (!resolved.isPublic()) throw TextbookPdfStreamException.unavailable();
        return resolved;
    }

    @Override
    public void streamReaderTicket(TextbookPdfReaderTicket ticket, String range, OutputStream output) {
        storage.write(ticket.objectKey(), range, output);
    }

    private TextbookPdfRow admin(String textbookId) {
        TextbookPdfRow row = mapper.selectAdmin(id(textbookId), visibleUser());
        if (row == null) throw failure(404, "TEXTBOOK_NOT_FOUND", "教材不存在或不可访问");
        return row;
    }
    private TextbookPdfRow lock(long id) {
        TextbookPdfRow row = mapper.lockAdmin(id, visibleUser());
        if (row == null) throw failure(404, "TEXTBOOK_NOT_FOUND", "教材不存在或不可访问");
        return row;
    }

    /** Persists only after the OSS upload has completed; this method runs inside a short database transaction. */
    private String persistUploadedPdf(long documentId, String objectKey, String fileName, long fileSize, String fileHash, long userId) {
        TextbookPdfRow textbook = lock(documentId);
        if (!"draft".equals(textbook.status())) {
            throw failure(409, "TEXTBOOK_PDF_UPLOAD_FORBIDDEN", "仅草稿教材允许上传原始PDF");
        }
        if (mapper.upsert(IdUtil.getSnowflakeNextId(), documentId, objectKey, fileName, fileSize, fileHash, userId) != 1) {
            throw failure(503, "TEXTBOOK_PDF_STORAGE_UNAVAILABLE", "教材PDF保存失败，请稍后重试", true, null);
        }
        return textbook.objectKey();
    }

    /** Deletes the attachment row in the same short transaction that validates the textbook state. */
    private String deleteUploadedPdf(long documentId) {
        TextbookPdfRow textbook = lock(documentId);
        if (!"draft".equals(textbook.status())) {
            throw failure(409, "TEXTBOOK_PDF_DELETE_FORBIDDEN", "仅草稿教材允许删除原始PDF");
        }
        if (textbook.objectKey() == null || textbook.objectKey().isBlank()) {
            throw failure(404, "TEXTBOOK_PDF_NOT_FOUND", "教材尚未上传原始PDF");
        }
        if (mapper.delete(documentId) != 1) {
            throw failure(409, "TEXTBOOK_PDF_DELETE_CONFLICT", "教材原始PDF已被其他操作变更，请刷新后重试");
        }
        return textbook.objectKey();
    }

    /** Queues private object cleanup only after its database change commits successfully. */
    private void enqueueObjectCleanup(long documentId, String objectKey, String reason) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            importPersistence.enqueueCleanup(objectKey, "unknown", reason);
        } catch (RuntimeException exception) {
            log.warn("Unable to enqueue textbook PDF cleanup, textbookId={}", documentId);
        }
    }

    /** Best-effort compensation when an uploaded object cannot be associated with the textbook. */
    private void deleteNewObjectAfterPersistenceFailure(long documentId, String objectKey) {
        try {
            storage.delete(objectKey);
        } catch (RuntimeException exception) {
            log.warn("Unable to delete unassociated textbook PDF, textbookId={}", documentId);
        }
    }
    private TextbookPdfRow adminRequirePdf(String textbookId) {
        TextbookPdfRow row = admin(textbookId);
        if (row.objectKey() == null) throw failure(404, "TEXTBOOK_PDF_NOT_FOUND", "教材尚未上传原始PDF");
        return row;
    }
    private TextbookPdfInfoVo info(TextbookPdfRow row) {
        return row.objectKey() == null ? new TextbookPdfInfoVo(false, null, null, null, null)
            : new TextbookPdfInfoVo(true, row.fileName(), row.fileSize(), row.fileHash(), row.uploadedTime());
    }
    private TextbookPdfReaderVo reader(TextbookPdfRow row) {
        return reader(row, TextbookPdfReaderTicket.PRIVATE_AUDIENCE);
    }

    private TextbookPdfReaderVo reader(TextbookPdfRow row, String audience) {
        try {
            String ticket = readerTicketStore.issue(new TextbookPdfReaderTicket(row.objectKey(), row.fileName(), row.fileSize(), audience), URL_TTL);
            return new TextbookPdfReaderVo(String.valueOf(row.documentId()), row.title(), row.fileName(),
                (TextbookPdfReaderTicket.PUBLIC_AUDIENCE.equals(audience) ? "/api/public/reader/textbook-pdfs/" : "/api/reader/textbook-pdfs/") + ticket,
                OffsetDateTime.now().plus(URL_TTL));
        } catch (RuntimeException e) {
            throw failure(503, "TEXTBOOK_PDF_STORAGE_UNAVAILABLE", "教材PDF暂不可阅读，请稍后重试", true, e);
        }
    }
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw failure(400, "TEXTBOOK_PDF_INVALID", "PDF文件不能为空");
        if (maxSizeBytes <= 0 || file.getSize() > maxSizeBytes) throw failure(413, "TEXTBOOK_PDF_TOO_LARGE", "PDF文件超过允许的大小限制");
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".pdf")) throw failure(400, "TEXTBOOK_PDF_INVALID", "仅支持PDF文件");
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(5);
            if (header.length != 5 || header[0] != '%' || header[1] != 'P' || header[2] != 'D' || header[3] != 'F' || header[4] != '-') {
                throw failure(400, "TEXTBOOK_PDF_INVALID", "文件不是有效PDF");
            }
        } catch (IOException e) { throw failure(400, "TEXTBOOK_PDF_INVALID", "PDF文件无法读取"); }
    }
    private static String safeName(String name) {
        String normalized = name == null ? "" : name.replace('\\', '/').trim();
        int separator = normalized.lastIndexOf('/');
        normalized = separator >= 0 ? normalized.substring(separator + 1) : normalized;
        if (normalized.isBlank()) return "textbook.pdf";
        if (normalized.length() > 500) throw failure(400, "TEXTBOOK_PDF_INVALID", "PDF文件名不能超过500字符");
        return normalized;
    }
    private static long id(String raw) { try { return Long.parseLong(raw); } catch (Exception e) { throw failure(400, "TEXTBOOK_PDF_REQUEST_INVALID", "textbookId格式不正确"); } }
    private static Long visibleUser() { return LoginHelper.isSuperAdmin() ? null : LoginHelper.getUserId(); }
    private static TextbookPdfException failure(int status, String code, String message) { return new TextbookPdfException(status, code, message); }
    private static TextbookPdfException failure(int status, String code, String message, boolean retryable, Throwable cause) { return new TextbookPdfException(status, code, message, retryable, cause); }
}
