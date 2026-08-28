package org.dromara.certmuse.catalog.service;

import java.util.List;
import org.dromara.certmuse.catalog.domain.KnowledgePointDirectoryRow;

/** Cross-domain read contract for learner-scoped knowledge-point directory labels. */
public interface KnowledgePointDirectoryCatalogService {

    /**
     * Reads the caller's current-goal scope and the requested visible directory records.
     *
     * @param userId authenticated learner ID
     * @param ids normalized distinct knowledge-point IDs
     * @return a result that distinguishes an absent current goal from an empty directory match
     */
    LookupResult lookupForCurrentGoal(long userId, List<Long> ids);

    /** Result of one scope-safe directory query. */
    record LookupResult(boolean currentGoalFound, List<KnowledgePointDirectoryRow> items) {
        public LookupResult {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
