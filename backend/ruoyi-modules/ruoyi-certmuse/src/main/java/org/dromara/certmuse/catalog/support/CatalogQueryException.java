package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.shared.web.CertMuseApiException;

public class CatalogQueryException extends CertMuseApiException {
    public CatalogQueryException(int status, String errorCode, String message) {
        this(status, errorCode, message, null);
    }

    public CatalogQueryException(int status, String errorCode, String message, String traceId) {
        super(status, errorCode, message, false, traceId, java.util.List.of(), null, null);
    }
}
