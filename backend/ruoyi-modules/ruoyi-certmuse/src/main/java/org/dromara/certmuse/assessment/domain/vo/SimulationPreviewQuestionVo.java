package org.dromara.certmuse.assessment.domain.vo;

/** One published simulation question stem, deliberately without options or grading content. */
public record SimulationPreviewQuestionVo(int questionOrder, String questionType, String stem) {
}
