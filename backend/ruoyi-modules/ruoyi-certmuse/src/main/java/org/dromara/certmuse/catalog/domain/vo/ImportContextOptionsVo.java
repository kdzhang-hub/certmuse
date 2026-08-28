package org.dromara.certmuse.catalog.domain.vo;

import java.util.List;

/**
 * Import context options response.
 */
public record ImportContextOptionsVo(
    List<SyllabusVersionOptionVo> syllabusVersions,
    List<ExamSubjectOptionVo> examSubjects,
    List<TextbookOptionVo> certifications
) {
    public ImportContextOptionsVo(List<SyllabusVersionOptionVo> syllabusVersions, List<ExamSubjectOptionVo> examSubjects) {
        this(syllabusVersions, examSubjects, List.of());
    }
}
