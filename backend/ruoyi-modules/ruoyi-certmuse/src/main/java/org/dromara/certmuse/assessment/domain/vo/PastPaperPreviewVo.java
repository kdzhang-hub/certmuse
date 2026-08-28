package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Complete public question presentation with no answer or analysis data. */
public record PastPaperPreviewVo(String collectionId, String revisionId, List<QuestionVo> questions) {
    public record QuestionVo(int questionOrder, String questionType, String stem, List<OptionVo> options, List<ImageVo> images) {}
    public record OptionVo(String label, String content) {}
    public record ImageVo(int sortOrder, String url, String alt) {}
}
