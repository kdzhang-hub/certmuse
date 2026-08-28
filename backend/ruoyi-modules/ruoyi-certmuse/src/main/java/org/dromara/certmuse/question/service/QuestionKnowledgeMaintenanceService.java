package org.dromara.certmuse.question.service;

import java.util.Collection;

/** Maintains current question revisions after a knowledge tree update. */
public interface QuestionKnowledgeMaintenanceService {

    /**
     * Returns invalid published revisions to draft without changing question content.
     *
     * @param deletedKnowledgePointIds knowledge points removed by the import
     * @param importBatchId import batch used for deterministic audit correlation
     * @param operatorId operator that confirmed the import
     * @return number of revisions returned to draft
     */
    int maintainAfterKnowledgeDeletion(
        Collection<Long> deletedKnowledgePointIds,
        long importBatchId,
        Long operatorId
    );
}
