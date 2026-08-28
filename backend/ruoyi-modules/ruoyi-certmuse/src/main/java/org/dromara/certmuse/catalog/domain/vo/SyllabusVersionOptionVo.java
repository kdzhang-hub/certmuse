package org.dromara.certmuse.catalog.domain.vo;

/**
 * Syllabus version option available to an import request.
 */
public record SyllabusVersionOptionVo(
    String id,
    String certificationId,
    String label,
    Boolean knowledgeTreeAvailable,
    Long knowledgePointCount
) {
    public SyllabusVersionOptionVo(String id, String label, Boolean knowledgeTreeAvailable, Long knowledgePointCount) {
        this(id, null, label, knowledgeTreeAvailable, knowledgePointCount);
    }
}
