package org.dromara.certmuse.question.domain.vo;

import java.util.List;

public record QuestionPreviewVo(
    String questionId, String revisionId, String stem, List<QuestionDetailVo.ImageVo> images,
    List<QuestionDetailVo.OptionVo> options, Object answer, String analysis, String reviewOpinion
) {
}
