package org.dromara.certmuse.catalog.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record TextbookChunkDetailVo(String id, String documentId, int chunkOrder, String heading,
                                    List<String> headingPath, String content, String contentHash,
                                    TextbookChunkListItemVo.SourceLocatorVo sourceLocator,
                                    List<TextbookChunkListItemVo.KnowledgePointVo> knowledgePoints,
                                    OffsetDateTime updateTime, boolean editable, boolean deletable,
                                    String operationDisabledReason) {}
