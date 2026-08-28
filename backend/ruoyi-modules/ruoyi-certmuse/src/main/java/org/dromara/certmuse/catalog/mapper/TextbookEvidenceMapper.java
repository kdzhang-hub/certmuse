package org.dromara.certmuse.catalog.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.catalog.domain.TextbookEmbeddingTaskRow;
import org.dromara.certmuse.catalog.domain.TextbookEvidenceCandidateRow;
import org.dromara.certmuse.catalog.domain.TextbookKnowledgeScopeRow;

/** PostgreSQL hybrid retrieval and embedding-task persistence. */
public interface TextbookEvidenceMapper {
    TextbookKnowledgeScopeRow selectKnowledgeScope(@Param("knowledgePointIds") List<Long> knowledgePointIds);
    List<TextbookEvidenceCandidateRow> selectLexicalCandidates(
        @Param("knowledgePointIds") List<Long> knowledgePointIds,
        @Param("syllabusVersionId") long syllabusVersionId,
        @Param("examSubjectId") long examSubjectId,
        @Param("strictKnowledge") boolean strictKnowledge,
        @Param("queryText") String queryText,
        @Param("limit") int limit);
    List<TextbookEvidenceCandidateRow> selectSemanticCandidates(
        @Param("knowledgePointIds") List<Long> knowledgePointIds,
        @Param("syllabusVersionId") long syllabusVersionId,
        @Param("examSubjectId") long examSubjectId,
        @Param("strictKnowledge") boolean strictKnowledge,
        @Param("embedding") String embedding,
        @Param("embeddingModel") String embeddingModel,
        @Param("limit") int limit);
    TextbookEmbeddingTaskRow claimEmbeddingTask(@Param("embeddingModel") String embeddingModel,
                                                 @Param("dimensions") int dimensions);
    int completeEmbeddingTask(@Param("chunkId") long chunkId, @Param("contentHash") String contentHash,
                              @Param("embeddingModel") String embeddingModel,
                              @Param("dimensions") int dimensions, @Param("embedding") String embedding);
    int retryEmbeddingTask(@Param("chunkId") long chunkId, @Param("errorCode") String errorCode,
                           @Param("maxAttempts") int maxAttempts);
    int queueDocumentEmbeddings(@Param("documentId") long documentId,
                                @Param("embeddingModel") String embeddingModel,
                                @Param("dimensions") int dimensions);
    int queueChunkEmbedding(@Param("chunkId") long chunkId, @Param("embeddingModel") String embeddingModel,
                            @Param("dimensions") int dimensions);
}
