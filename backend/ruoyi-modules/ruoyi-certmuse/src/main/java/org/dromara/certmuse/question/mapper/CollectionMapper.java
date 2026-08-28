package org.dromara.certmuse.question.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.question.domain.CollectionIdempotencyRow;
import org.dromara.certmuse.question.domain.CollectionItemRow;
import org.dromara.certmuse.question.domain.CollectionListRow;
import org.dromara.certmuse.question.domain.CollectionRevisionRow;
import org.dromara.certmuse.question.domain.bo.CollectionQueryBo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface CollectionMapper {
    List<CollectionListRow> selectCollections(@Param("query") CollectionQueryBo query,
        @Param("certificationId") Long certificationId, @Param("syllabusVersionId") Long syllabusVersionId,
        @Param("limit") int limit, @Param("offset") long offset);
    long countCollections(@Param("query") CollectionQueryBo query,
        @Param("certificationId") Long certificationId, @Param("syllabusVersionId") Long syllabusVersionId);
    List<Long> selectManagedCollectionIds(@Param("query") CollectionQueryBo query,
        @Param("certificationId") Long certificationId, @Param("syllabusVersionId") Long syllabusVersionId,
        @Param("limit") int limit, @Param("offset") long offset);
    long countManagedCollections(@Param("query") CollectionQueryBo query,
        @Param("certificationId") Long certificationId, @Param("syllabusVersionId") Long syllabusVersionId);
    List<CollectionListRow> selectManagedCollectionRevisions(@Param("collectionIds") Collection<Long> collectionIds);
    CollectionRevisionRow selectRevision(long revisionId);
    CollectionRevisionRow lockRevision(long revisionId);
    CollectionRevisionRow selectDisplayRevision(long collectionId);
    CollectionRevisionRow selectCurrentPublished(long collectionId);
    CollectionRevisionRow selectPendingReviewRevision(long collectionId);
    CollectionRevisionRow lockCurrentPublished(long collectionId);
    List<CollectionRevisionRow> lockPublishedRevisions(long collectionId);
    List<CollectionRevisionRow> lockPendingRevisionsByQuestionRevision(long questionRevisionId);
    List<CollectionRevisionRow> selectRevisions(long collectionId);
    List<CollectionItemRow> selectItems(long revisionId);
    List<CollectionItemRow> selectQuestionRevisionMetadata(@Param("ids") Collection<Long> ids,
        @Param("visibleUserId") Long visibleUserId);
    Long lockCollection(long collectionId);
    Long lockDraftRevisionForRename(long collectionId);
    int updateCollectionName(@Param("collectionId") long collectionId, @Param("name") String name);
    int countCertification(long certificationId);
    int countSyllabusInCertification(@Param("syllabusId") long syllabusId, @Param("certificationId") long certificationId);
    List<Long> selectActiveSubjectIds(long certificationId);
    int nextRevisionNo(long collectionId);

    int insertCollection(@Param("id") long id, @Param("certificationId") long certificationId,
        @Param("syllabusVersionId") long syllabusVersionId, @Param("code") String code,
        @Param("name") String name, @Param("type") String type, @Param("examYear") Integer examYear,
        @Param("examMonth") Integer examMonth, @Param("paperTypeCode") String paperTypeCode,
        @Param("paperTypeName") String paperTypeName);
    /** Compatibility bridge for existing non-past-paper draft creation. */
    default int insertCollection(long id, long certificationId, long syllabusVersionId, String code, String name, String type) {
        return insertCollection(id, certificationId, syllabusVersionId, code, name, type, null, null, null, null);
    }
    int updatePastPaperMetadata(@Param("collectionId") long collectionId, @Param("type") String type,
                                @Param("examYear") Integer examYear, @Param("examMonth") Integer examMonth,
                                @Param("paperTypeCode") String paperTypeCode, @Param("paperTypeName") String paperTypeName);
    int insertRevision(@Param("id") long id, @Param("collectionId") long collectionId,
        @Param("revisionNo") int revisionNo, @Param("name") String name, @Param("type") String type,
        @Param("certificationId") long certificationId, @Param("syllabusVersionId") long syllabusVersionId,
        @Param("durationMinutes") int durationMinutes, @Param("questionCount") int questionCount,
        @Param("totalScore") BigDecimal totalScore);
    int updateDraft(@Param("revisionId") long revisionId, @Param("rowVersion") long rowVersion,
        @Param("name") String name, @Param("type") String type,
        @Param("certificationId") long certificationId, @Param("syllabusVersionId") long syllabusVersionId,
        @Param("durationMinutes") int durationMinutes, @Param("questionCount") int questionCount,
        @Param("totalScore") BigDecimal totalScore);
    void deleteItems(long revisionId);
    int deleteDraftRevision(long revisionId);
    int countRevisions(long collectionId);
    int deleteCollection(long collectionId);
    void insertItem(@Param("id") long id, @Param("revisionId") long revisionId,
        @Param("questionRevisionId") long questionRevisionId, @Param("itemOrder") int itemOrder,
        @Param("reportScore") BigDecimal reportScore, @Param("answerSchema") String answerSchema);
    int markSubmitted(@Param("revisionId") long revisionId, @Param("userId") Long userId);
    int markRejected(@Param("revisionId") long revisionId, @Param("userId") Long userId,
        @Param("opinion") String opinion);
    int markPublished(@Param("revisionId") long revisionId, @Param("userId") Long userId);
    int markPublishedAsDraft(@Param("revisionId") long revisionId);
    void upsertCurrent(@Param("collectionId") long collectionId, @Param("revisionId") long revisionId);
    int deleteCurrent(@Param("collectionId") long collectionId, @Param("revisionId") long revisionId);
    void insertAudit(@Param("id") long id, @Param("operatorId") Long operatorId,
        @Param("action") String action, @Param("revisionId") long revisionId,
        @Param("beforeData") String beforeData, @Param("afterData") String afterData,
        @Param("traceId") String traceId);

    CollectionIdempotencyRow selectIdempotency(@Param("action") String action, @Param("requestId") String requestId);
    int insertIdempotency(@Param("id") long id, @Param("action") String action,
        @Param("requestId") String requestId, @Param("payloadHash") String payloadHash,
        @Param("resourceId") Long resourceId, @Param("expiresTime") OffsetDateTime expiresTime);
    void completeIdempotency(@Param("id") long id, @Param("resourceId") Long resourceId,
        @Param("responseStatus") int responseStatus, @Param("responseBody") String responseBody);
    void insertReviewAudit(@Param("id") long id, @Param("userId") Long userId, @Param("action") String action,
                           @Param("revisionId") long revisionId, @Param("beforeData") String beforeData,
                           @Param("afterData") String afterData, @Param("traceId") String traceId);
}
