package org.dromara.certmuse.catalog.domain.vo;

import java.util.List;

/** Context choices for manual textbook imports. */
public record TextbookImportContextOptionsVo(
    List<SyllabusVersionOptionVo> syllabusVersions,
    List<ExamSubjectOptionVo> examSubjects,
    List<DefaultSubjectMappingVo> defaultSubjectMappings,
    List<ReplaceableTextbookDraftVo> replaceableDrafts,
    List<TextbookOptionVo> certifications
) {
    public TextbookImportContextOptionsVo(
        List<SyllabusVersionOptionVo> syllabusVersions,
        List<ExamSubjectOptionVo> examSubjects,
        List<DefaultSubjectMappingVo> defaultSubjectMappings,
        List<ReplaceableTextbookDraftVo> replaceableDrafts
    ) {
        this(syllabusVersions, examSubjects, defaultSubjectMappings, replaceableDrafts, List.of());
    }
}
