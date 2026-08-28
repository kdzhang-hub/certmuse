package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.catalog.domain.vo.ImportErrorVo;
import org.dromara.common.core.domain.R;

/**
 * Import response factory.
 */
public final class ImportResponses {

    private ImportResponses() {
    }

    public static R<ImportErrorVo> fail(ImportException exception) {
        R<ImportErrorVo> response = new R<>();
        response.setCode(exception.code());
        response.setMsg(exception.getMessage());
        response.setData(new ImportErrorVo(
            exception.errorCode(),
            exception.retryable(),
            exception.traceId(),
            exception.fieldErrors()
        ));
        return response;
    }
}
