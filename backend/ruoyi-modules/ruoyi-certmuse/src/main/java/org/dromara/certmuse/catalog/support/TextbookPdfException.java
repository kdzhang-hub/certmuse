package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.shared.web.CertMuseApiException;

/** Expected failures for the independent original-PDF attachment capability. */
public class TextbookPdfException extends CertMuseApiException {
    public TextbookPdfException(int status, String code, String message) {
        this(status, code, message, false, null);
    }
    public TextbookPdfException(int status, String code, String message, boolean retryable, Throwable cause) {
        super(status, code, message, retryable, null, null, null, cause);
    }
}
