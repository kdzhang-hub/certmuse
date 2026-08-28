package org.dromara.certmuse.catalog.domain.vo;

import java.util.List;

/** Context choices used by the multi-subject question ZIP import screen. */
public record QuestionImportContextOptionsVo(
    List<SyllabusVersionOptionVo> knowledgeSyllabusVersions,
    List<TextbookOptionVo> certifications
) {
    public QuestionImportContextOptionsVo(List<SyllabusVersionOptionVo> knowledgeSyllabusVersions) {
        this(knowledgeSyllabusVersions, List.of());
    }
}
