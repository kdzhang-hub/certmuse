package org.dromara.certmuse.question.service.impl;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.question.domain.QuestionKnowledgeMaintenanceRow;
import org.dromara.certmuse.question.mapper.QuestionMapper;
import org.dromara.certmuse.question.service.QuestionKnowledgeMaintenanceService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Question-domain implementation of knowledge relation maintenance. */
@Service
@RequiredArgsConstructor
public class QuestionKnowledgeMaintenanceServiceImpl implements QuestionKnowledgeMaintenanceService {
    private final QuestionMapper mapper;

    @Override
    public int maintainAfterKnowledgeDeletion(
        Collection<Long> deletedKnowledgePointIds,
        long importBatchId,
        Long operatorId
    ) {
        if (deletedKnowledgePointIds == null || deletedKnowledgePointIds.isEmpty()) return 0;
        List<QuestionKnowledgeMaintenanceRow> rows = mapper.selectInvalidPublishedRevisions(deletedKnowledgePointIds);
        if (rows.isEmpty()) return 0;
        for (QuestionKnowledgeMaintenanceRow row : rows) {
            row.setEventId(IdUtil.getSnowflakeNextId());
            row.setAuditId(IdUtil.getSnowflakeNextId());
            row.setOperatorId(operatorId);
            row.setRequestId(uuid("knowledge-import:" + importBatchId + ":" + row.getRevisionId()));
            row.setTraceId(uuid("knowledge-import-trace:" + importBatchId + ":" + row.getRevisionId()));
        }
        int updated = mapper.batchReturnKnowledgeInvalidRevisionsToDraft(rows, operatorId);
        if (updated != rows.size()) throw new IllegalStateException("Question knowledge maintenance update was incomplete");
        if (mapper.insertKnowledgeInvalidRevisionEvents(rows) != rows.size()) {
            throw new IllegalStateException("Question knowledge maintenance event insert was incomplete");
        }
        if (mapper.insertKnowledgeInvalidAudits(rows) != rows.size()) {
            throw new IllegalStateException("Question knowledge maintenance audit insert was incomplete");
        }
        return updated;
    }

    private static String uuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
