package org.dromara.certmuse.catalog.domain.vo;

import java.util.List;

public record TextbookChunkListItemVo(String id, String documentId, int chunkOrder, String heading,
                                      List<String> headingPath, String contentPreview, Integer pageStart,
                                      Integer pageEnd, SourceLocatorVo sourceLocator,
                                      List<KnowledgePointVo> knowledgePoints, boolean mapped) {
    public record SourceLocatorVo(String sourceType, String sourceKey, Integer lineStart, Integer lineEnd) {}
    public record KnowledgePointVo(String id, Integer subjectNo, String code, String title) {}
}
