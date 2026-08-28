package org.dromara.certmuse.catalog.mapper;

import cn.hutool.core.util.IdUtil;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.catalog.domain.CmAsyncJob;
import org.dromara.certmuse.catalog.domain.CmIdempotencyRecord;
import org.dromara.certmuse.catalog.domain.CmImportBatch;
import org.dromara.certmuse.catalog.domain.PaperImportRow;
import org.dromara.certmuse.catalog.domain.PaperQuestionReuse;
import org.dromara.certmuse.catalog.domain.KnowledgePointInsert;
import org.dromara.certmuse.catalog.domain.KnowledgePointUpdate;
import org.dromara.certmuse.catalog.domain.KnowledgeImportDiffCounts;
import org.dromara.certmuse.catalog.domain.KnowledgeImportDiffInsert;
import org.dromara.certmuse.catalog.domain.KnowledgeImportDiffRow;
import org.dromara.certmuse.catalog.domain.KnowledgeImportPointRow;
import org.dromara.certmuse.catalog.domain.TextbookChunkInsert;
import org.dromara.certmuse.catalog.domain.TextbookChunkKnowledgeInsert;
import org.dromara.certmuse.catalog.domain.TextbookImportDocument;
import org.dromara.certmuse.catalog.domain.TextbookImportRecordData;
import org.dromara.certmuse.catalog.domain.TextbookKnowledgePointLookup;
import org.dromara.certmuse.catalog.domain.TextbookPreviewKnowledgePointLookup;
import org.dromara.certmuse.catalog.domain.TextbookPreviewRecord;
import org.dromara.certmuse.catalog.domain.TextbookRecordResultUpdate;
import org.dromara.certmuse.catalog.domain.vo.CompletedImportBatchDetailVo;
import org.dromara.certmuse.catalog.domain.vo.CompletedImportBatchVo;
import org.dromara.certmuse.catalog.domain.vo.ExamSubjectOptionVo;
import org.dromara.certmuse.catalog.domain.vo.ImportBatchSyllabusOptionVo;
import org.dromara.certmuse.catalog.domain.vo.ImportBatchTypeCountsVo;
import org.dromara.certmuse.catalog.domain.vo.ImportBatchUploaderOptionVo;
import org.dromara.certmuse.catalog.domain.vo.ImportIssueVo;
import org.dromara.certmuse.catalog.domain.vo.SyllabusVersionOptionVo;
import org.dromara.certmuse.catalog.domain.vo.QuestionImportOptionRow;
import org.dromara.certmuse.catalog.domain.vo.ReplaceableTextbookDraftVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookImportPreviewDocumentVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookImportPreviewSummaryVo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Content import persistence mapper.
 */
@Mapper
public interface ImportMapper {

    long countCompletedBatches(
        @Param("keyword") String keyword,
        @Param("importType") String importType,
        @Param("syllabusVersionId") Long syllabusVersionId,
        @Param("uploaderId") Long uploaderId,
        @Param("completedStartTime") OffsetDateTime completedStartTime,
        @Param("completedEndTime") OffsetDateTime completedEndTime,
        @Param("visibleUserId") Long visibleUserId
    );

    List<CompletedImportBatchVo> selectCompletedBatches(
        @Param("keyword") String keyword,
        @Param("importType") String importType,
        @Param("syllabusVersionId") Long syllabusVersionId,
        @Param("uploaderId") Long uploaderId,
        @Param("completedStartTime") OffsetDateTime completedStartTime,
        @Param("completedEndTime") OffsetDateTime completedEndTime,
        @Param("visibleUserId") Long visibleUserId,
        @Param("ascending") boolean ascending,
        @Param("limit") int limit,
        @Param("offset") long offset
    );

    ImportBatchTypeCountsVo selectCompletedTypeCounts(@Param("visibleUserId") Long visibleUserId);

    List<ImportBatchSyllabusOptionVo> selectCompletedSyllabusOptions(
        @Param("visibleUserId") Long visibleUserId
    );

    List<ImportBatchUploaderOptionVo> selectCompletedUploaderOptions(
        @Param("visibleUserId") Long visibleUserId
    );

    CompletedImportBatchDetailVo selectCompletedBatchDetail(
        @Param("id") long id,
        @Param("visibleUserId") Long visibleUserId
    );

    List<SyllabusVersionOptionVo> selectSyllabusOptions();

    List<ExamSubjectOptionVo> selectExamSubjectOptions(@Param("syllabusVersionId") long syllabusVersionId);

    List<ExamSubjectOptionVo> selectExamSubjectOptionsByCertification(@Param("certificationId") long certificationId);

    List<org.dromara.certmuse.catalog.domain.vo.TextbookOptionVo> selectCertificationOptions();

    List<org.dromara.certmuse.catalog.domain.vo.TextbookOptionVo> selectCertificationOptionsWithoutSyllabus();

    Long selectSyllabusByCertification(@Param("certificationId") long certificationId);

    int insertSyllabus(@Param("id") long id, @Param("certificationId") long certificationId,
                       @Param("userId") long userId, @Param("deptId") Long deptId);

    List<QuestionImportOptionRow> selectQuestionImportOptions();

    Long selectSyllabusCertification(@Param("id") long id);

    Long selectLatestSyllabusForCertification(@Param("certificationId") long certificationId);

    Long selectSubjectCertification(@Param("id") long id);

    String selectCertificationNameBySubject(@Param("id") long id);

    String selectCertificationNameBySyllabus(@Param("id") long id);

    String selectCertificationName(@Param("id") long id);

    String selectSyllabusDisplayName(@Param("id") long id);

    Integer selectSubjectNo(@Param("subjectId") long subjectId, @Param("syllabusVersionId") long syllabusVersionId);

    CmImportBatch selectByRequestId(@Param("requestId") String requestId);

    CmIdempotencyRecord selectIdempotency(
        @Param("actionCode") String actionCode,
        @Param("requestId") String requestId
    );

    CmImportBatch selectVisible(@Param("id") long id, @Param("userId") Long userId);

    CmImportBatch selectById(@Param("id") long id);

    CmImportBatch selectByIdForUpdate(@Param("id") long id);

    int insertBatch(
        @Param("id") long id,
        @Param("requestId") String requestId,
        @Param("syllabusId") long syllabusId,
        @Param("sourceFilePath") String sourceFilePath,
        @Param("sourceFileHash") String sourceFileHash,
        @Param("sourceFileName") String sourceFileName,
        @Param("sourceFileSize") long sourceFileSize,
        @Param("sourceFileMime") String sourceFileMime,
        @Param("parseConfig") String parseConfig,
        @Param("userId") long userId,
        @Param("deptId") Long deptId,
        @Param("createTime") OffsetDateTime createTime
    );

    int insertQuestionBatch(
        @Param("id") long id, @Param("requestId") String requestId, @Param("syllabusId") Long syllabusId,
        @Param("certificationId") long certificationId,
        @Param("sourceFilePath") String sourceFilePath,
        @Param("sourceFileHash") String sourceFileHash, @Param("sourceFileName") String sourceFileName,
        @Param("sourceFileSize") long sourceFileSize, @Param("sourceFileMime") String sourceFileMime,
        @Param("parseConfig") String parseConfig, @Param("userId") long userId, @Param("deptId") Long deptId,
        @Param("createTime") OffsetDateTime createTime
    );

    int insertPaperBatch(
        @Param("id") long id, @Param("requestId") String requestId, @Param("syllabusId") long syllabusId,
        @Param("certificationId") long certificationId,
        @Param("sourceFilePath") String sourceFilePath,
        @Param("sourceFileHash") String sourceFileHash, @Param("sourceFileName") String sourceFileName,
        @Param("sourceFileSize") long sourceFileSize, @Param("sourceFileMime") String sourceFileMime,
        @Param("parseConfig") String parseConfig, @Param("userId") long userId, @Param("deptId") Long deptId,
        @Param("createTime") OffsetDateTime createTime
    );

    int insertPaperImport(@Param("batchId") long batchId, @Param("certificationId") long certificationId,
        @Param("collectionName") String collectionName, @Param("collectionType") String collectionType,
        @Param("durationMinutes") int durationMinutes, @Param("examYear") Integer examYear,
        @Param("examMonth") Integer examMonth, @Param("paperTypeCode") String paperTypeCode,
        @Param("paperTypeName") String paperTypeName);

    default int insertPaperImport(long batchId, long certificationId, String collectionName, String collectionType,
                                  int durationMinutes) {
        return insertPaperImport(batchId, certificationId, collectionName, collectionType, durationMinutes,
            null, null, null, null);
    }

    PaperImportRow selectPaperImport(@Param("batchId") long batchId);

    PaperImportRow selectPaperImportForUpdate(@Param("batchId") long batchId);

    int completePaperImport(@Param("batchId") long batchId, @Param("generatedCount") int generatedCount,
        @Param("collectionId") long collectionId, @Param("revisionId") long revisionId);

    int updatePaperImportResult(@Param("batchId") long batchId, @Param("collectionId") long collectionId,
        @Param("revisionId") long revisionId);

    TextbookImportDocument selectTextbookDocument(
        @Param("id") long id,
        @Param("visibleUserId") Long visibleUserId
    );

    TextbookImportDocument selectTextbookDocumentForUpdate(@Param("id") long id);

    List<ReplaceableTextbookDraftVo> selectReplaceableTextbookDrafts(
        @Param("syllabusVersionId") long syllabusVersionId,
        @Param("visibleUserId") Long visibleUserId
    );

    List<TextbookKnowledgePointLookup> selectTextbookKnowledgePointLookups(
        @Param("examSubjectIds") List<Long> examSubjectIds
    );

    List<TextbookPreviewKnowledgePointLookup> selectTextbookPreviewKnowledgePointLookups(
        @Param("batchId") long batchId,
        @Param("examSubjectIds") List<Long> examSubjectIds,
        @Param("syllabusNumbers") List<String> syllabusNumbers
    );

    int insertTextbookDocument(
        @Param("id") long id,
        @Param("certificationId") long certificationId,
        @Param("syllabusVersionId") Long syllabusVersionId,
        @Param("title") String title,
        @Param("edition") String edition,
        @Param("userId") long userId,
        @Param("deptId") Long deptId,
        @Param("createTime") OffsetDateTime createTime
    );

    int insertTextbookBatch(
        @Param("id") long id,
        @Param("requestId") String requestId,
        @Param("documentId") Long documentId,
        @Param("certificationId") long certificationId,
        @Param("syllabusVersionId") Long syllabusVersionId,
        @Param("sourceFilePath") String sourceFilePath,
        @Param("sourceFileHash") String sourceFileHash,
        @Param("sourceFileName") String sourceFileName,
        @Param("sourceFileSize") long sourceFileSize,
        @Param("sourceFileMime") String sourceFileMime,
        @Param("parseConfig") String parseConfig,
        @Param("userId") long userId,
        @Param("deptId") Long deptId,
        @Param("createTime") OffsetDateTime createTime
    );

    int bindTextbookSyllabus(
        @Param("id") long id,
        @Param("syllabusVersionId") long syllabusVersionId,
        @Param("userId") long userId
    );

    int bindTextbookDocument(@Param("id") long id, @Param("documentId") long documentId);

    int submitTextbookForAutoPublish(
        @Param("id") long id,
        @Param("userId") long userId,
        @Param("confirmedTime") OffsetDateTime confirmedTime
    );

    int approveTextbookAfterImport(@Param("id") long id);

    int publishTextbookAfterImport(@Param("id") long id);

    int insertIdempotency(
        @Param("id") long id,
        @Param("actionCode") String actionCode,
        @Param("requestId") String requestId,
        @Param("payloadHash") String payloadHash
    );

    int completeIdempotency(
        @Param("actionCode") String actionCode,
        @Param("requestId") String requestId,
        @Param("resourceId") long resourceId,
        @Param("responseBody") String responseBody
    );

    int start(@Param("id") long id);

    int acceptConfirmation(
        @Param("id") long id,
        @Param("userId") long userId,
        @Param("confirmedTime") OffsetDateTime confirmedTime
    );

    long countKnowledgePoints(@Param("syllabusVersionId") long syllabusVersionId);

    List<KnowledgeImportPointRow> selectCurrentKnowledgePoints(@Param("syllabusVersionId") long syllabusVersionId);

    List<KnowledgeImportPointRow> selectImportedKnowledgePoints(@Param("batchId") long batchId);

    int deleteKnowledgeImportDiffs(@Param("batchId") long batchId);

    int insertKnowledgeImportDiffs(@Param("rows") List<KnowledgeImportDiffInsert> rows);

    int updateKnowledgeImportHashes(
        @Param("batchId") long batchId,
        @Param("baselineHash") String baselineHash,
        @Param("resolutionHash") String resolutionHash
    );

    long countKnowledgeImportDiffs(
        @Param("batchId") long batchId,
        @Param("examSubjectId") Long examSubjectId,
        @Param("resolutionStatus") String resolutionStatus,
        @Param("action") String action,
        @Param("excludeUnchanged") boolean excludeUnchanged,
        @Param("minScore") BigDecimal minScore,
        @Param("maxScore") BigDecimal maxScore
    );

    List<KnowledgeImportDiffRow> selectKnowledgeImportDiffs(
        @Param("batchId") long batchId,
        @Param("examSubjectId") Long examSubjectId,
        @Param("resolutionStatus") String resolutionStatus,
        @Param("action") String action,
        @Param("excludeUnchanged") boolean excludeUnchanged,
        @Param("minScore") BigDecimal minScore,
        @Param("maxScore") BigDecimal maxScore,
        @Param("limit") int limit,
        @Param("offset") long offset
    );

    List<KnowledgeImportDiffRow> selectAllKnowledgeImportDiffs(@Param("batchId") long batchId);

    KnowledgeImportDiffCounts selectKnowledgeImportDiffCounts(@Param("batchId") long batchId);

    KnowledgeImportDiffRow selectKnowledgeImportDiffForUpdate(
        @Param("batchId") long batchId,
        @Param("diffId") long diffId
    );

    KnowledgeImportDiffRow selectPendingDeleteDiffForOldForUpdate(
        @Param("batchId") long batchId,
        @Param("knowledgePointId") long knowledgePointId
    );

    int deletePendingKnowledgeImportDiff(@Param("batchId") long batchId, @Param("diffId") long diffId);

    KnowledgeImportPointRow selectCurrentKnowledgePoint(
        @Param("syllabusVersionId") long syllabusVersionId,
        @Param("knowledgePointId") long knowledgePointId
    );

    long countConfirmedKnowledgePointUse(
        @Param("batchId") long batchId,
        @Param("knowledgePointId") long knowledgePointId,
        @Param("excludeDiffId") long excludeDiffId
    );

    int updateKnowledgeImportDiffResolution(
        @Param("batchId") long batchId,
        @Param("diffId") long diffId,
        @Param("action") String action,
        @Param("confirmedKnowledgePointId") Long confirmedKnowledgePointId,
        @Param("resolutionDecision") String resolutionDecision,
        @Param("userId") long userId
    );

    int cascadeKnowledgeImportDiffResolution(
        @Param("batchId") long batchId,
        @Param("rootDiffId") long rootDiffId,
        @Param("resolutionDecision") String resolutionDecision,
        @Param("userId") long userId
    );

    long countPendingKnowledgeImportDiffs(@Param("batchId") long batchId);

    int updateKnowledgeImportResolutionHash(@Param("batchId") long batchId, @Param("resolutionHash") String resolutionHash);

    Long lockSyllabusVersion(@Param("syllabusVersionId") long syllabusVersionId);

    int softDeleteActiveKnowledgePoints(@Param("syllabusVersionId") long syllabusVersionId, @Param("userId") Long userId);

    int updateKnowledgePoints(@Param("rows") List<KnowledgePointUpdate> rows);

    List<String> selectSuccessfulRecordJson(@Param("batchId") long batchId);

    int insertKnowledgePoints(@Param("rows") List<KnowledgePointInsert> rows);

    int completeImport(@Param("id") long id, @Param("generatedCount") int generatedCount);

    int enqueueJob(
        @Param("id") long id,
        @Param("jobType") String jobType,
        @Param("businessKey") String businessKey,
        @Param("payload") String payload
    );

    int enqueueJobAt(
        @Param("id") long id,
        @Param("jobType") String jobType,
        @Param("businessKey") String businessKey,
        @Param("payload") String payload,
        @Param("nextRunTime") OffsetDateTime nextRunTime
    );

    CmAsyncJob claimJob(@Param("jobType") String jobType);

    int succeedJob(@Param("id") long id);

    int retryJob(@Param("id") long id, @Param("error") String error);

    int failJob(@Param("id") long id, @Param("error") String error);

    int recoverExpiredJobs();

    int clearBatchData(@Param("batchId") long batchId);

    int updateStage(
        @Param("id") long id,
        @Param("expectedStatus") String expectedStatus,
        @Param("status") String status,
        @Param("stage") String stage,
        @Param("progress") BigDecimal progress
    );

    int updateProgress(
        @Param("id") long id,
        @Param("expectedStatus") String expectedStatus,
        @Param("stage") String stage,
        @Param("progress") BigDecimal progress
    );

    int insertRecord(
        @Param("id") long id,
        @Param("batchId") long batchId,
        @Param("lineNo") int lineNo,
        @Param("sourceKey") String sourceKey,
        @Param("rawRecord") String rawRecord,
        @Param("rawHash") String rawHash
    );

    int insertIssue(
        @Param("id") long id,
        @Param("batchId") long batchId,
        @Param("recordId") Long recordId,
        @Param("code") String code,
        @Param("fieldPath") String fieldPath,
        @Param("severity") String severity,
        @Param("message") String message
    );

    default long createRecord(long batchId, int lineNo, String sourceKey, String rawRecord, String rawHash) {
        long id = IdUtil.getSnowflakeNextId();
        insertRecord(id, batchId, lineNo, sourceKey, rawRecord, rawHash);
        return id;
    }

    default void createIssue(
        long batchId,
        Long recordId,
        String code,
        String fieldPath,
        String severity,
        String message
    ) {
        insertIssue(IdUtil.getSnowflakeNextId(), batchId, recordId, code, fieldPath, severity, message);
    }

    int markRecord(@Param("id") long id, @Param("status") String status);

    int finish(@Param("id") long id, @Param("expectedStatus") String expectedStatus);

    int finishQuestion(@Param("id") long id, @Param("expectedStatus") String expectedStatus);

    int finishPaper(@Param("id") long id, @Param("expectedStatus") String expectedStatus);

    int finishTextbook(@Param("id") long id, @Param("expectedStatus") String expectedStatus);

    int acceptQuestionConfirmation(@Param("id") long id, @Param("userId") long userId, @Param("confirmedTime") OffsetDateTime confirmedTime);

    int acceptPaperConfirmation(@Param("id") long id, @Param("userId") long userId, @Param("confirmedTime") OffsetDateTime confirmedTime);

    int acceptTextbookConfirmation(
        @Param("id") long id,
        @Param("userId") long userId,
        @Param("confirmedTime") OffsetDateTime confirmedTime
    );

    TextbookImportPreviewDocumentVo selectTextbookPreviewDocument(@Param("batchId") long batchId);

    TextbookImportPreviewSummaryVo selectTextbookPreviewSummary(@Param("batchId") long batchId);

    long countTextbookPreviewRecords(@Param("batchId") long batchId);

    List<TextbookPreviewRecord> selectTextbookPreviewRecords(
        @Param("batchId") long batchId,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    List<TextbookImportRecordData> selectPendingTextbookRecords(@Param("batchId") long batchId);

    long countDocumentChunks(@Param("documentId") long documentId);

    int deleteTextbookImages(@Param("documentId") long documentId);

    int deleteTextbookKnowledgeRelations(@Param("documentId") long documentId);

    int deleteTextbookChunks(@Param("documentId") long documentId);

    int insertTextbookChunks(@Param("rows") List<TextbookChunkInsert> rows);

    int insertTextbookKnowledgeRelations(@Param("rows") List<TextbookChunkKnowledgeInsert> rows);

    int updateTextbookRecordResults(@Param("rows") List<TextbookRecordResultUpdate> rows);

    int completeTextbookImport(@Param("id") long id, @Param("generatedCount") int generatedCount);

    List<String> selectSuccessfulQuestionResult(@Param("batchId") long batchId);

    int updateRecordResult(@Param("id") long id, @Param("resultData") String resultData, @Param("status") String status);

    int updateQuestionRecordResult(
        @Param("id") long id, @Param("subjectNo") int subjectNo, @Param("examSubjectId") long examSubjectId,
        @Param("resultData") String resultData, @Param("status") String status
    );

    Long findKnowledgePoint(@Param("syllabusVersionId") long syllabusVersionId, @Param("examSubjectId") long examSubjectId, @Param("code") String code);

    boolean isKnowledgePointLeaf(@Param("id") long id);

    int countQuestionSource(@Param("sourceHash") String sourceHash);

    String findQuestionSemanticDuplicate(@Param("examSubjectId") long examSubjectId, @Param("semanticHash") String semanticHash);

    List<PaperQuestionReuse> findPaperQuestionReuses(@Param("examSubjectId") long examSubjectId,
        @Param("syllabusVersionId") long syllabusVersionId, @Param("semanticHash") String semanticHash,
        @Param("visibleUserId") Long visibleUserId);

    int insertQuestion(@Param("id") long id, @Param("questionCode") String questionCode, @Param("examSubjectId") long examSubjectId, @Param("sourceHash") String sourceHash, @Param("userId") long userId, @Param("deptId") Long deptId);

    int insertQuestionRevision(@Param("id") long id, @Param("questionId") long questionId, @Param("questionType") String questionType, @Param("stem") String stem, @Param("answer") String answer, @Param("analysis") String analysis, @Param("sourceName") String sourceName, @Param("sourceExternalId") String sourceExternalId, @Param("sourceLocator") String sourceLocator, @Param("contentHash") String contentHash, @Param("semanticHash") String semanticHash, @Param("userId") long userId);

    int insertQuestionEvent(@Param("id") long id, @Param("questionId") long questionId, @Param("revisionId") long revisionId, @Param("requestId") String requestId, @Param("userId") long userId);

    int insertQuestionOption(@Param("id") long id, @Param("revisionId") long revisionId, @Param("label") String label, @Param("text") String text, @Param("sortOrder") int sortOrder, @Param("userId") long userId);

    int insertQuestionImage(@Param("id") long id, @Param("revisionId") long revisionId, @Param("imageOrder") int imageOrder, @Param("storagePath") String storagePath, @Param("altText") String altText, @Param("userId") long userId);

    int insertQuestionKnowledge(@Param("id") long id, @Param("revisionId") long revisionId, @Param("questionId") long questionId, @Param("knowledgePointId") long knowledgePointId, @Param("examSubjectId") long examSubjectId, @Param("role") String role, @Param("sortOrder") int sortOrder);

    int completeQuestionImport(@Param("id") long id, @Param("generatedCount") int generatedCount);

    long countImageReferences(@Param("storagePath") String storagePath);

    int failActive(@Param("id") long id, @Param("traceId") String traceId);

    int failImporting(@Param("id") long id, @Param("traceId") String traceId);

    long countIssues(
        @Param("batchId") long batchId,
        @Param("severity") String severity,
        @Param("issueCode") String issueCode
    );

    List<ImportIssueVo> selectIssues(
        @Param("batchId") long batchId,
        @Param("severity") String severity,
        @Param("issueCode") String issueCode,
        @Param("order") String order,
        @Param("asc") boolean asc,
        @Param("limit") int limit,
        @Param("offset") int offset
    );
}
