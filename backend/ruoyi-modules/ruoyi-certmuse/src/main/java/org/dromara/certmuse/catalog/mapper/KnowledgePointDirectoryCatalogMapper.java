package org.dromara.certmuse.catalog.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.catalog.domain.KnowledgePointDirectoryRow;

/** Read-only persistence boundary for learner-visible knowledge-point directory labels. */
@Mapper
public interface KnowledgePointDirectoryCatalogMapper {

    /**
     * Resolves one learner's current-goal scope and matching visible directory records in one read query.
     * A row with a null {@code id} denotes an absent current goal or an empty match set.
     */
    List<KnowledgePointDirectoryRow> selectForCurrentGoal(@Param("userId") long userId,
                                                           @Param("ids") List<Long> ids);
}
