package org.dromara.certmuse.assessment.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

import java.util.List;

/** Intentionally excludes grading snapshots, answer keys, analysis and knowledge conclusions. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiagnosticItemVo(int questionOrder, String questionType, String stem, String difficulty,
                               List<OptionVo> options, List<ImageVo> images, JsonNode answer,
                               String answerSaveState, long sessionVersion) {
    public record OptionVo(String label, String content, int sortOrder) { }
    public record ImageVo(String url, String alt, int sortOrder) { }
}
