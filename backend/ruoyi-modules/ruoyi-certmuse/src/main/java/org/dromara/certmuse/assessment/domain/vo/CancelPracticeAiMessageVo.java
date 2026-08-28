package org.dromara.certmuse.assessment.domain.vo;

/** Result of cancelling a generated assistant message. */
public record CancelPracticeAiMessageVo(String assistantMessageId, String status) {
}
