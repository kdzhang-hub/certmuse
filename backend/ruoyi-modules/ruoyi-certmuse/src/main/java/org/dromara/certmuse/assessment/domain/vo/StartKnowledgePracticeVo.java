package org.dromara.certmuse.assessment.domain.vo;

/** Result of atomically creating a self-practice session. */
public record StartKnowledgePracticeVo(String sessionId, int totalCount, String answerPath) {
}
