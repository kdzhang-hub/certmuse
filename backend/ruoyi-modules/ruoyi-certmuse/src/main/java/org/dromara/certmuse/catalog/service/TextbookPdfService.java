package org.dromara.certmuse.catalog.service;

import java.util.List;
import java.io.OutputStream;
import org.dromara.certmuse.catalog.domain.vo.LearningTextbookPdfListItemVo;
import org.dromara.certmuse.catalog.domain.vo.PublicTextbookCertificationVo;
import org.dromara.certmuse.catalog.domain.vo.PublicTextbookPdfListItemVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookPdfInfoVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookPdfReaderVo;
import org.dromara.certmuse.catalog.support.TextbookPdfReaderTicket;
import org.springframework.web.multipart.MultipartFile;

/** Independent original-PDF attachment use cases for textbooks. */
public interface TextbookPdfService {
    TextbookPdfInfoVo adminInfo(String textbookId);
    TextbookPdfInfoVo upload(String textbookId, MultipartFile file);
    /** Deletes only the original-PDF attachment of a draft textbook. */
    void delete(String textbookId);
    TextbookPdfReaderVo adminReader(String textbookId);
    List<LearningTextbookPdfListItemVo> learnerList(long userId, String keyword);
    TextbookPdfReaderVo learnerReader(long userId, String textbookId);
    /** Lists enabled certifications for the temporary anonymous browsing context. */
    List<PublicTextbookCertificationVo> publicCertifications();
    /** Lists published original PDFs for one anonymous browsing certification. */
    List<PublicTextbookPdfListItemVo> publicList(String certificationId, String keyword);
    /** Issues a public-only reader ticket for a published original PDF. */
    TextbookPdfReaderVo publicReader(String textbookId);
    /** Resolves an opaque, already-authorized browser reader ticket. */
    TextbookPdfReaderTicket resolveReaderTicket(String ticket);
    /** Resolves only tickets deliberately issued for anonymous reading. */
    TextbookPdfReaderTicket resolvePublicReaderTicket(String ticket);
    /** Streams one complete object or a single byte range to the browser response. */
    void streamReaderTicket(TextbookPdfReaderTicket ticket, String range, OutputStream output);
}
