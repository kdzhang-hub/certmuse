package org.dromara.certmuse.question.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.question.domain.QuestionKnowledgeMaintenanceRow;
import org.dromara.certmuse.question.domain.QuestionRows;
import org.dromara.certmuse.question.domain.bo.QuestionQueryBo;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface QuestionMapper {
    List<QuestionKnowledgeMaintenanceRow> selectInvalidPublishedRevisions(
        @Param("deletedKnowledgePointIds") Collection<Long> deletedKnowledgePointIds
    );

    int batchReturnKnowledgeInvalidRevisionsToDraft(
        @Param("rows") List<QuestionKnowledgeMaintenanceRow> rows,
        @Param("operatorId") Long operatorId
    );

    int insertKnowledgeInvalidRevisionEvents(@Param("rows") List<QuestionKnowledgeMaintenanceRow> rows);

    int insertKnowledgeInvalidAudits(@Param("rows") List<QuestionKnowledgeMaintenanceRow> rows);

    List<QuestionRows.ListItem> selectQuestions(@Param("query") QuestionQueryBo query,
        @Param("certificationId") Long certificationId, @Param("syllabusId") Long syllabusId, @Param("subjectId") Long subjectId,
        @Param("knowledgeId") Long knowledgeId, @Param("visibleUserId") Long visibleUserId,
        @Param("limit") int limit, @Param("offset") long offset);
    long countQuestions(@Param("query") QuestionQueryBo query,
        @Param("certificationId") Long certificationId, @Param("syllabusId") Long syllabusId, @Param("subjectId") Long subjectId,
        @Param("knowledgeId") Long knowledgeId, @Param("visibleUserId") Long visibleUserId);

    QuestionRows.Revision selectRevision(@Param("questionId") long questionId,
        @Param("revisionId") Long revisionId, @Param("visibleUserId") Long visibleUserId);
    QuestionRows.Revision lockRevision(@Param("questionId") long questionId,
        @Param("revisionId") long revisionId, @Param("visibleUserId") Long visibleUserId);
    QuestionRows.Revision lockReviewRevision(@Param("revisionId") long revisionId,
        @Param("visibleUserId") Long visibleUserId);
    List<QuestionRows.Revision> lockReviewRevisions(@Param("revisionIds") Collection<Long> revisionIds,
        @Param("visibleUserId") Long visibleUserId);
    QuestionRows.Revision selectWorkingRevision(@Param("questionId") long questionId);
    List<QuestionRows.Option> selectOptions(long revisionId);
    List<QuestionRows.Image> selectImages(long revisionId);
    List<QuestionRows.Knowledge> selectKnowledge(long revisionId);
    List<QuestionRows.IdLabel> selectExamSubjectOptions(long certificationId);
    QuestionRows.IdLabel selectSyllabusLabel(long syllabusVersionId);
    List<QuestionRows.Knowledge> selectKnowledgeMetadata(@Param("ids") Collection<Long> ids);
    int countSubjectInSyllabus(@Param("syllabusId") long syllabusId, @Param("subjectId") long subjectId);
    int countQuestionReferences(long questionId);
    int countPublishedHistory(long questionId);
    int countRevisions(long questionId);

    int updateRevision(@Param("revisionId") long revisionId, @Param("rowVersion") long rowVersion,
        @Param("status") String status, @Param("questionType") String questionType,
        @Param("difficulty") String difficulty, @Param("estimatedSeconds") Integer estimatedSeconds,
        @Param("stem") String stem, @Param("answer") String answer, @Param("analysis") String analysis,
        @Param("commonMistakes") String commonMistakes, @Param("contentHash") String contentHash, @Param("semanticHash") String semanticHash,
        @Param("userId") Long userId);
    int updateQuestionSubject(@Param("questionId") long questionId, @Param("subjectId") long subjectId,
        @Param("userId") Long userId);
    int markReviewSubmitted(@Param("revisionId") long revisionId, @Param("userId") Long userId);
    int markReviewPublished(@Param("revisionId") long revisionId, @Param("userId") Long userId);
    int markReviewRejected(@Param("revisionId") long revisionId, @Param("userId") Long userId,
        @Param("opinion") String opinion);
    int markPublishedDraft(@Param("revisionId") long revisionId, @Param("userId") Long userId);
    int softDeleteQuestion(@Param("questionId") long questionId, @Param("userId") Long userId);
    int nextRevisionNo(long questionId);
    int insertRevision(@Param("id") long id, @Param("questionId") long questionId,
        @Param("revisionNo") int revisionNo, @Param("questionType") String questionType,
        @Param("difficulty") String difficulty, @Param("estimatedSeconds") Integer estimatedSeconds,
        @Param("stem") String stem, @Param("answer") String answer, @Param("analysis") String analysis,
        @Param("commonMistakes") String commonMistakes, @Param("contentHash") String contentHash, @Param("semanticHash") String semanticHash,
        @Param("source") QuestionRows.Revision source, @Param("userId") Long userId);
    void deleteOptions(long revisionId);
    void deleteQuestionKnowledge(long revisionId);
    void insertOption(@Param("id") long id, @Param("revisionId") long revisionId,
        @Param("label") String label, @Param("content") String content,
        @Param("sortOrder") int sortOrder, @Param("userId") Long userId);
    void insertImage(@Param("id") long id, @Param("revisionId") long revisionId,
        @Param("sortOrder") int sortOrder, @Param("sourceUrl") String sourceUrl,
        @Param("alt") String alt, @Param("storagePath") String storagePath, @Param("userId") Long userId);
    void insertKnowledge(@Param("id") long id, @Param("revisionId") long revisionId,
        @Param("questionId") long questionId, @Param("knowledgeId") long knowledgeId,
        @Param("subjectId") long subjectId, @Param("role") String role, @Param("sortOrder") int sortOrder);
    void insertRevisionEvent(@Param("id") long id, @Param("questionId") long questionId,
        @Param("revisionId") long revisionId, @Param("eventType") String eventType,
        @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
        @Param("opinion") String opinion, @Param("userId") Long userId,
        @Param("requestId") String requestId, @Param("traceId") String traceId);

    QuestionRows.Idempotency selectIdempotency(@Param("action") String action, @Param("requestId") String requestId);
    int insertIdempotency(@Param("id") long id, @Param("action") String action,
        @Param("requestId") String requestId, @Param("payloadHash") String payloadHash,
        @Param("resourceType") String resourceType, @Param("resourceId") long resourceId,
        @Param("expiresTime") OffsetDateTime expiresTime);
    void completeIdempotency(@Param("id") long id, @Param("responseStatus") int responseStatus,
        @Param("responseBody") String responseBody);
    QuestionRows.Idempotency selectReviewIdempotency(String requestId);
    int insertReviewIdempotency(@Param("id") long id, @Param("action") String action,
        @Param("requestId") String requestId, @Param("payloadHash") String payloadHash,
        @Param("questionId") long questionId, @Param("expiresTime") OffsetDateTime expiresTime);
    void insertAudit(@Param("id") long id, @Param("userId") Long userId,
        @Param("questionId") long questionId, @Param("beforeData") String beforeData,
        @Param("afterData") String afterData, @Param("traceId") String traceId);
    void insertReviewAudit(@Param("id") long id, @Param("userId") Long userId,
        @Param("action") String action, @Param("revisionId") long revisionId,
        @Param("beforeData") String beforeData, @Param("afterData") String afterData,
        @Param("traceId") String traceId);
}
