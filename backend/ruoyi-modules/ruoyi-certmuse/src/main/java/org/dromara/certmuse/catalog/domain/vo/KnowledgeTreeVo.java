package org.dromara.certmuse.catalog.domain.vo;

import java.util.List;

public record KnowledgeTreeVo(
    SyllabusOverviewVo syllabus,
    KnowledgeTreeSummaryVo summary,
    List<KnowledgeSubjectVo> subjects,
    List<KnowledgeTreeNodeVo> nodes
) {
}
