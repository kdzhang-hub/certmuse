package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Cursor page of AI chat messages ordered by sequence ascending. */
public record PracticeAiMessagePageVo(
    List<PracticeAiMessageVo> items,
    boolean hasMore,
    Integer nextBeforeSequence
) {
}
