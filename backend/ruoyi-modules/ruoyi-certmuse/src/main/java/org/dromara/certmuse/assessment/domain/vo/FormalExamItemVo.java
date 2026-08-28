package org.dromara.certmuse.assessment.domain.vo;

import java.util.List;

/** Frozen formal-exam item without grading disclosure. */
public record FormalExamItemVo(
    int questionOrder,
    int totalCount,
    String questionType,
    String stem,
    String selectionMode,
    List<OptionVo> options,
    List<ImageVo> images,
    List<String> choiceValue,
    String textValue,
    long sessionVersion,
    String gradingStatus
) {
    public record OptionVo(String label, String content) { }
    public record ImageVo(int sortOrder, String url, String alt) { }
}
