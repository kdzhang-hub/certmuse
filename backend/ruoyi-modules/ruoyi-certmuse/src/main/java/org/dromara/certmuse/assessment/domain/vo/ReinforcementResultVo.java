package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Completed reinforcement round result and continuation capability. */
public record ReinforcementResultVo(String roundId, int roundNo, int correctCount, int actualCount,
                                    int correctRate, List<ItemVo> items,
                                    List<ReinforcementSuggestionVo.KnowledgePointVo> knowledgePoints,
                                    boolean hasMoreCandidates, String continueAction,
                                    String returnPath) {
    public record ItemVo(int questionOrder, boolean correct) {}
}
