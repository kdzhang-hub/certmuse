package org.dromara.certmuse.assessment.domain.vo;

/** Created or restored reinforcement round. */
public record ReinforcementRoundVo(String roundId, int roundNo, String status, int actualCount,
                                   String reinforcementSessionId, String answerPath,
                                   String sourceSessionId, int sourceQuestionOrder) {}
