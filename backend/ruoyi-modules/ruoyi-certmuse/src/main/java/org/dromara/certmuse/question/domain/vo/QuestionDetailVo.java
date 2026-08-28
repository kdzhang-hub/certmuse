package org.dromara.certmuse.question.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record QuestionDetailVo(
    String questionId, String revisionId, int revisionNo, String rowVersion, String questionCode,
    String syllabusVersionId, String syllabusVersionName, String certificationId, String certificationName,
    String examSubjectId, String examSubjectName, boolean examSubjectEditable,
    List<IdLabelVo> examSubjectOptions, String questionType, String difficulty, Integer estimatedSeconds,
    String stem, List<ImageVo> images, List<OptionVo> options, Object answer, String analysis,
    String commonMistakes, List<KnowledgeBindingVo> knowledgeBindings,
    String status, String reviewOpinion, String editableMode, OffsetDateTime updatedTime
) {
    public record IdLabelVo(String id, String label) { }
    public record ImageVo(String imageId, int sortOrder, String url, String alt) { }
    public record OptionVo(String label, String content, int sortOrder) { }
    public record KnowledgeBindingVo(String knowledgePointId, String knowledgePointLabel, String relationRole, int sortOrder) { }
}
