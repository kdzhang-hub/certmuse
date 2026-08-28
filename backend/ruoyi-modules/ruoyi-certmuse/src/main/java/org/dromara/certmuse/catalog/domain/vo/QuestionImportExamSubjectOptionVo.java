package org.dromara.certmuse.catalog.domain.vo;

import java.util.List;

/** A question-import subject and the compatible syllabus versions. */
public record QuestionImportExamSubjectOptionVo(
    String id,
    Integer subjectNo,
    String label,
    List<SyllabusVersionOptionVo> syllabusVersions
) {
}
