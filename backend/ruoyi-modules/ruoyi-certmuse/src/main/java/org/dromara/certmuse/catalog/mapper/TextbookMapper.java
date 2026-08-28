package org.dromara.certmuse.catalog.mapper;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.catalog.domain.TextbookRows;
import org.dromara.certmuse.catalog.domain.TextbookChunkKnowledgeInsert;
import org.dromara.certmuse.catalog.domain.bo.TextbookQueryBo;
import org.dromara.certmuse.catalog.domain.vo.TextbookListVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookOptionVo;

@Mapper
public interface TextbookMapper {
    List<TextbookListVo> selectTextbooks(@Param("query") TextbookQueryBo query,
        @Param("certificationId") Long certificationId, @Param("syllabusVersionId") Long syllabusVersionId, @Param("createBy") Long createBy,
        @Param("visibleUserId") Long visibleUserId, @Param("limit") int limit, @Param("offset") long offset);
    long countTextbooks(@Param("query") TextbookQueryBo query, @Param("certificationId") Long certificationId, @Param("syllabusVersionId") Long syllabusVersionId,
        @Param("createBy") Long createBy, @Param("visibleUserId") Long visibleUserId);
    List<TextbookOptionVo> selectCertificationOptions(@Param("visibleUserId") Long visibleUserId);
    List<TextbookOptionVo> selectCreatorOptions(@Param("visibleUserId") Long visibleUserId);
    TextbookRows.Detail selectTextbook(@Param("id") long id, @Param("visibleUserId") Long visibleUserId);
    Long lockTextbook(@Param("id") long id, @Param("visibleUserId") Long visibleUserId);
    Long selectActiveCertification(@Param("certificationId") long certificationId);
    long countTextbookKnowledgeMappings(@Param("documentId") long documentId);
    int updateTextbook(@Param("id") long id, @Param("title") String title,
                       @Param("certificationId") long certificationId, @Param("clearSyllabusVersion") boolean clearSyllabusVersion,
                       @Param("userId") Long userId);
    int publishTextbook(@Param("id") long id, @Param("userId") Long userId);
    int takeTextbookOffline(@Param("id") long id, @Param("userId") Long userId);
    TextbookRows.Scope selectKnowledgeScope(@Param("knowledgePointId") long knowledgePointId);
    Long selectSubjectSyllabus(@Param("documentId") long documentId, @Param("examSubjectId") long examSubjectId);
    List<TextbookRows.Chunk> selectChunks(@Param("documentId") long documentId,
        @Param("knowledgePointId") Long knowledgePointId, @Param("examSubjectId") Long examSubjectId,
        @Param("includeDescendants") boolean includeDescendants, @Param("hasKnowledgePoint") Boolean hasKnowledgePoint, @Param("keyword") String keyword,
        @Param("limit") int limit, @Param("offset") long offset);
    long countChunks(@Param("documentId") long documentId, @Param("knowledgePointId") Long knowledgePointId,
        @Param("examSubjectId") Long examSubjectId, @Param("includeDescendants") boolean includeDescendants,
        @Param("hasKnowledgePoint") Boolean hasKnowledgePoint, @Param("keyword") String keyword);
    TextbookRows.Chunk selectChunk(@Param("documentId") long documentId, @Param("chunkId") long chunkId);
    int updateChunk(@Param("documentId") long documentId, @Param("chunkId") long chunkId,
        @Param("updateTime") OffsetDateTime updateTime, @Param("heading") String heading,
        @Param("content") String content, @Param("contentHash") String contentHash, @Param("userId") Long userId);
    long countKnowledgePoints(@Param("syllabusVersionId") long syllabusVersionId, @Param("ids") Collection<Long> ids);
    int deleteChunkKnowledgeByChunk(@Param("chunkId") long chunkId);
    int insertChunkKnowledge(@Param("rows") List<TextbookChunkKnowledgeInsert> rows);
    long countChunksForDelete(@Param("documentId") long documentId, @Param("ids") Collection<Long> ids);
    int deleteImages(@Param("documentId") long documentId, @Param("ids") Collection<Long> ids);
    int deleteChunkKnowledge(@Param("documentId") long documentId, @Param("ids") Collection<Long> ids);
    int deleteChunks(@Param("documentId") long documentId, @Param("ids") Collection<Long> ids);
    int softDeleteTextbook(@Param("id") long id, @Param("userId") Long userId);
}
