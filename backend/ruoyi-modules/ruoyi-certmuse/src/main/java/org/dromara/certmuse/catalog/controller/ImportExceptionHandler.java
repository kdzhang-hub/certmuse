package org.dromara.certmuse.catalog.controller;

import java.util.List;
import org.dromara.certmuse.catalog.domain.vo.ImportFieldErrorVo;
import org.dromara.certmuse.catalog.domain.vo.ImportErrorVo;
import org.dromara.certmuse.catalog.support.ImportException;
import org.dromara.certmuse.shared.web.ApiFieldErrors;
import org.dromara.certmuse.shared.web.CertMuseErrorResponses;
import org.dromara.certmuse.shared.web.CertMuseAdviceOrder;
import org.dromara.common.core.domain.R;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/**
 * Maps import exceptions to the versioned API error shape.
 */
@Order(CertMuseAdviceOrder.DOMAIN)
@RestControllerAdvice(assignableTypes = ImportController.class)
public class ImportExceptionHandler {

    @ExceptionHandler(ImportException.class)
    public ResponseEntity<R<ImportErrorVo>> handle(ImportException exception) {
        return CertMuseErrorResponses.fail(exception, traceId -> new ImportErrorVo(
            exception.errorCode(), exception.retryable(), traceId, exception.fieldErrors()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<R<ImportErrorVo>> handleBinding(BindException exception) {
        var fieldErrors = exception.getFieldErrors().stream()
            .map(ImportExceptionHandler::toImportFieldError)
            .toList();
        if (fieldErrors.isEmpty()) {
            fieldErrors = java.util.List.of(
                new ImportFieldErrorVo("request", "INVALID_FORMAT", "请求参数格式不正确")
            );
        }
        return handle(new ImportException(
            400,
            "IMPORT_CONTEXT_INVALID",
            "导入上下文不合法",
            false,
            null,
            fieldErrors
        ));
    }

    /**
     * Multipart requests can fail before {@link ImportController#create} is invoked.
     * Keep those client-correctable failures inside the import API error contract
     * instead of leaking into the global 500 handler.
     */
    @ExceptionHandler({
        MissingRequestHeaderException.class,
        HttpMessageNotReadableException.class,
        MultipartException.class
    })
    public ResponseEntity<R<ImportErrorVo>> handleRequestException(Exception exception) {
        if (exception instanceof MaxUploadSizeExceededException) {
            return handle(new ImportException(413, "IMPORT_FILE_TOO_LARGE", "导入文件超过大小限制"));
        }
        List<ImportFieldErrorVo> fieldErrors = exception instanceof MissingRequestHeaderException header
            ? List.of(new ImportFieldErrorVo(header.getHeaderName(), "REQUIRED", "请求头不能为空"))
            : List.of();
        return handle(new ImportException(
            400,
            "IMPORT_CONTEXT_INVALID",
            "导入请求参数不合法",
            false,
            null,
            fieldErrors
        ));
    }

    private static ImportFieldErrorVo toImportFieldError(FieldError error) {
        var mapped = ApiFieldErrors.from(error);
        return new ImportFieldErrorVo(mapped.field(), mapped.code(), mapped.message());
    }
}
