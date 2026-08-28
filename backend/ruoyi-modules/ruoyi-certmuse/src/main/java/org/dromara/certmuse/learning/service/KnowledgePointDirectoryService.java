package org.dromara.certmuse.learning.service;

import org.dromara.certmuse.learning.domain.vo.KnowledgePointDirectoryVo;

/** Learner use case for resolving current-goal knowledge-point directory labels. */
public interface KnowledgePointDirectoryService {

    /** Parses one ids query value and resolves its visible current-goal directory items. */
    KnowledgePointDirectoryVo lookup(long userId, String rawIds);
}
