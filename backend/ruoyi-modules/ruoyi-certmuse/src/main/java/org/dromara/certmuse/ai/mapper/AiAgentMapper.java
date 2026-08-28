package org.dromara.certmuse.ai.mapper;

import org.apache.ibatis.annotations.Param;

/** Durable Agent run, step, and evidence audit persistence. */
public interface AiAgentMapper {
    int insertRun(@Param("id") long id, @Param("taskType") String taskType,
                  @Param("resourceType") String resourceType, @Param("resourceId") long resourceId,
                  @Param("modelName") String modelName, @Param("promptVersion") String promptVersion);
    int insertStep(@Param("id") long id, @Param("runId") long runId, @Param("stepOrder") int stepOrder,
                   @Param("stepType") String stepType, @Param("status") String status,
                   @Param("resultCount") Integer resultCount, @Param("durationMs") long durationMs,
                   @Param("errorCode") String errorCode);
    int insertEvidence(@Param("id") long id, @Param("runId") long runId, @Param("chunkId") long chunkId,
                       @Param("rank") int rank, @Param("lexicalScore") Object lexicalScore,
                       @Param("semanticScore") Object semanticScore, @Param("fusedScore") Object fusedScore,
                       @Param("contentHash") String contentHash, @Param("citationSnapshot") String citationSnapshot,
                       @Param("excerpt") String excerpt);
    int succeedRun(@Param("id") long id, @Param("durationMs") long durationMs);
    int failRun(@Param("id") long id, @Param("durationMs") long durationMs, @Param("errorCode") String errorCode);
}
