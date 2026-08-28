package org.dromara.certmuse.learning.mapper;

import org.apache.ibatis.annotations.Param;

/** Writes deduplicated error facts for formal learning attempts. */
public interface MistakeFactMapper {
    int insert(@Param("id") long id, @Param("userId") long userId, @Param("goalId") long goalId,
               @Param("attemptId") long attemptId, @Param("knowledgePointId") long knowledgePointId,
               @Param("groupKey") String groupKey);
}
