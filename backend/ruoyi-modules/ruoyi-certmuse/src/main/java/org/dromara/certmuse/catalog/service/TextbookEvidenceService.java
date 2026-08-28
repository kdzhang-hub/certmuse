package org.dromara.certmuse.catalog.service;

import java.util.List;
import org.dromara.certmuse.catalog.domain.TextbookEvidence;
import org.dromara.certmuse.catalog.domain.TextbookEvidenceQuery;

/** Retrieves published, syllabus-scoped textbook evidence for AI use cases. */
public interface TextbookEvidenceService {
    List<TextbookEvidence> retrieve(TextbookEvidenceQuery query);
    void queueDocument(long documentId);
    void queueChunk(long chunkId);
}
