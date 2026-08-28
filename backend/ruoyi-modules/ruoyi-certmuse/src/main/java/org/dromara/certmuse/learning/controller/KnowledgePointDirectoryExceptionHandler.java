package org.dromara.certmuse.learning.controller;

import java.util.List;
import org.dromara.certmuse.learning.domain.vo.KnowledgePointDirectoryErrorVo;
import org.dromara.certmuse.learning.support.KnowledgePointDirectoryException;
import org.dromara.certmuse.shared.web.CertMuseErrorResponses;
import org.dromara.common.core.domain.R;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps U07 failures to the frozen directory-lookup error envelope. */
@Order(100)
@RestControllerAdvice(assignableTypes = KnowledgePointDirectoryController.class)
public class KnowledgePointDirectoryExceptionHandler {

    @ExceptionHandler(KnowledgePointDirectoryException.class)
    public ResponseEntity<R<KnowledgePointDirectoryErrorVo>> handle(KnowledgePointDirectoryException exception) {
        KnowledgePointDirectoryErrorVo data = exception.data();
        return CertMuseErrorResponses.fail(exception, traceId -> new KnowledgePointDirectoryErrorVo(
            data.errorCode(), data.retryable(), traceId, data.fieldErrors()));
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<R<KnowledgePointDirectoryErrorVo>> serializationFailure(
        HttpMessageNotWritableException exception
    ) {
        return handle(new KnowledgePointDirectoryException(500, "KNOWLEDGE_POINT_LOOKUP_SYSTEM_FAILURE",
            "知识点目录暂时不可用", true, List.of(), exception));
    }

}
