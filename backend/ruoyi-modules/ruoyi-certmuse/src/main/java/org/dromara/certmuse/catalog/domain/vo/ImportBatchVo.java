package org.dromara.certmuse.catalog.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Import batch response.
 */
public record ImportBatchVo(
    String id,
    String documentId,
    String importType,
    String mode,
    String syllabusVersionId,
    String examSubjectId,
    String knowledgeSyllabusVersionId,
    List<DerivedQuestionSubjectVo> derivedSubjects,
    String templateVersion,
    String status,
    boolean reused,
    OffsetDateTime createTime
) {
    public ImportBatchVo(
        String id,
        String importType,
        String syllabusVersionId,
        String examSubjectId,
        String knowledgeSyllabusVersionId,
        List<DerivedQuestionSubjectVo> derivedSubjects,
        String templateVersion,
        String status,
        boolean reused,
        OffsetDateTime createTime
    ) {
        this(
            id, null, importType, null, syllabusVersionId, examSubjectId, knowledgeSyllabusVersionId,
            derivedSubjects, templateVersion, status, reused, createTime
        );
    }
}
