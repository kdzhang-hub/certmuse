package org.dromara.certmuse.catalog.domain.bo;

import lombok.Data;

@Data
public class TextbookChunkQueryBo {
    private String knowledgePointId;
    private String examSubjectId;
    private Boolean includeDescendants;
    private Boolean hasKnowledgePoint;
    private String keyword;
    private Integer pageNum;
    private Integer pageSize;
}
