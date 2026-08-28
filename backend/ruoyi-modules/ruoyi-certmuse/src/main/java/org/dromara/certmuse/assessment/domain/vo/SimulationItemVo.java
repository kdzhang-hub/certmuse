package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** One frozen simulation question and its submitted answer state. */
public record SimulationItemVo(int questionOrder, String questionType, String stem, List<OptionVo> options,
                               String answerText, List<String> selectedOptions, String gradingStatus) {
    public record OptionVo(String label, String content) { }
}
