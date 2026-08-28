package org.dromara.certmuse.learning.domain.vo;

import java.util.List;

/** Paginated learner mistake projection. */
public record MistakeListVo(List<RowVo> rows, long total) {
    public record RowVo(String questionId, String stemPreview, List<KnowledgePointVo> knowledgePoints,
                        String status, long wrongCount, long skipCount, List<String> sources,
                        String lastWrongAt, boolean canCorrect, String blockReason) {}
    public record KnowledgePointVo(String id, String name) {}
}
