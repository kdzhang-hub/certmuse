package org.dromara.certmuse.catalog.domain;

import lombok.Data;

/** Knowledge point data used while reconciling an import with the current tree. */
@Data
public class KnowledgeImportPointRow {
    private Long knowledgePointId;
    private Long importRecordId;
    private long examSubjectId;
    private Long parentKnowledgePointId;
    private String parentSyllabusNumber;
    private String parentSyllabusTitle;
    private String syllabusNumber;
    private String syllabusTitle;
    private int treeDepth;
    private int sortOrder;
    private String description;
    private Integer importance;
    private String status;
    private long rowVersion;
}
