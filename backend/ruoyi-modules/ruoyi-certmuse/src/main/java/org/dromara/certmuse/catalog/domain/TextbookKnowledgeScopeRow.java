package org.dromara.certmuse.catalog.domain;

import lombok.Data;

/** Syllabus and subject scope derived from an owned frozen knowledge point. */
@Data
public class TextbookKnowledgeScopeRow {
    private Long syllabusVersionId;
    private Long examSubjectId;
}
