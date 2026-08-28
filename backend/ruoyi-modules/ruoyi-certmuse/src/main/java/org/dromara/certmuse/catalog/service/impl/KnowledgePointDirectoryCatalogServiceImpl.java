package org.dromara.certmuse.catalog.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.catalog.domain.KnowledgePointDirectoryRow;
import org.dromara.certmuse.catalog.mapper.KnowledgePointDirectoryCatalogMapper;
import org.dromara.certmuse.catalog.service.KnowledgePointDirectoryCatalogService;
import org.springframework.stereotype.Service;

/** Default implementation of the learner directory read contract. */
@Service
@RequiredArgsConstructor
public class KnowledgePointDirectoryCatalogServiceImpl implements KnowledgePointDirectoryCatalogService {
    private final KnowledgePointDirectoryCatalogMapper mapper;

    @Override
    public LookupResult lookupForCurrentGoal(long userId, List<Long> ids) {
        List<KnowledgePointDirectoryRow> rows = mapper.selectForCurrentGoal(userId, ids);
        boolean goalFound = rows.stream().anyMatch(KnowledgePointDirectoryRow::currentGoalFound);
        return new LookupResult(goalFound, rows.stream().filter(row -> row.id() != null).toList());
    }
}
