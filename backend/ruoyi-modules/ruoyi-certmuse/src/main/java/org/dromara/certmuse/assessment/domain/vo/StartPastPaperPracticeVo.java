package org.dromara.certmuse.assessment.domain.vo;

/** Receipt for starting or resuming a past-paper practice session. */
public record StartPastPaperPracticeVo(String sessionId, int totalCount, String answerPath) {}
