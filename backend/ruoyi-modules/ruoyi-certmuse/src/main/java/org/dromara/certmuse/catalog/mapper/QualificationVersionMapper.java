package org.dromara.certmuse.catalog.mapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.catalog.domain.CmIdempotencyRecord;
import org.dromara.certmuse.catalog.domain.QualificationRow;
import org.dromara.certmuse.catalog.domain.ReferenceBlockerRow;
import org.dromara.certmuse.catalog.domain.ReferenceCountRow;
import org.dromara.certmuse.catalog.domain.SyllabusVersionRow;
import org.dromara.certmuse.catalog.domain.bo.QualificationQueryBo;

/** Persistence operations for M01 qualification/version management. */
public interface QualificationVersionMapper {
    List<QualificationRow> selectQualifications(@Param("query") QualificationQueryBo query,
                                                @Param("limit") int limit, @Param("offset") long offset);
    long countQualifications(@Param("query") QualificationQueryBo query);
    List<SyllabusVersionRow> selectVersions(@Param("certificationIds") Collection<Long> certificationIds);
    List<ReferenceCountRow> selectQualificationReferenceCounts(@Param("ids") Collection<Long> ids);
    List<ReferenceCountRow> selectVersionReferenceCounts(@Param("ids") Collection<Long> ids);
    QualificationRow selectQualification(@Param("id") long id);
    QualificationRow lockQualification(@Param("id") long id);
    Long findQualificationIdByCode(@Param("code") String code, @Param("excludeId") Long excludeId);
    Long findQualificationIdByName(@Param("name") String name, @Param("excludeId") Long excludeId);
    SyllabusVersionRow selectVersion(@Param("certificationId") long certificationId, @Param("id") long id);
    SyllabusVersionRow lockVersion(@Param("certificationId") long certificationId, @Param("id") long id);
    int insertQualification(@Param("id") long id, @Param("code") String code, @Param("name") String name,
        @Param("level") String level, @Param("status") String status, @Param("sortOrder") int sortOrder,
        @Param("userId") Long userId, @Param("deptId") Long deptId);
    int insertExamSubject(@Param("id") long id, @Param("certificationId") long certificationId,
        @Param("code") String code, @Param("name") String name, @Param("userId") Long userId);
    int updateQualification(@Param("id") long id, @Param("code") String code, @Param("name") String name,
        @Param("level") String level, @Param("status") String status, @Param("sortOrder") int sortOrder,
        @Param("userId") Long userId);
    String setQualificationCascadeDelete(@Param("enabled") boolean enabled);
    int deleteQualification(@Param("id") long id);
    int deleteKnowledgeImportDiffsByQualification(@Param("certificationId") long certificationId);
    int insertVersion(@Param("id") long id, @Param("certificationId") long certificationId,
        @Param("name") String name, @Param("publishedDate") LocalDate publishedDate, @Param("userId") Long userId,
        @Param("deptId") Long deptId);
    int updateVersion(@Param("id") long id, @Param("certificationId") long certificationId,
        @Param("name") String name, @Param("publishedDate") LocalDate publishedDate, @Param("userId") Long userId);
    int deleteVersion(@Param("id") long id, @Param("certificationId") long certificationId);
    int deleteKnowledgeImportDiffsByVersion(@Param("syllabusVersionId") long syllabusVersionId);
    List<String> selectSourceObjectKeysByVersion(@Param("syllabusVersionId") long syllabusVersionId);
    List<String> selectImageObjectKeysByVersion(@Param("syllabusVersionId") long syllabusVersionId);
    List<String> selectSourceObjectKeysByQualification(@Param("certificationId") long certificationId);
    List<String> selectImageObjectKeysByQualification(@Param("certificationId") long certificationId);
    long countImageObjectKeyReferences(@Param("objectKey") String objectKey);
    List<ReferenceBlockerRow> selectQualificationBlockers(@Param("id") long id);
    List<ReferenceBlockerRow> selectVersionBlockers(@Param("id") long id);
    List<ReferenceBlockerRow> selectVersionDeleteBlockers(@Param("id") long id);
    CmIdempotencyRecord selectIdempotency(@Param("action") String action, @Param("requestId") String requestId);
    int insertIdempotency(@Param("id") long id, @Param("action") String action, @Param("requestId") String requestId,
        @Param("payloadHash") String payloadHash, @Param("resourceType") String resourceType,
        @Param("resourceId") Long resourceId, @Param("expiresTime") OffsetDateTime expiresTime);
    int completeIdempotency(@Param("action") String action, @Param("requestId") String requestId,
        @Param("resourceId") Long resourceId, @Param("responseBody") String responseBody);
}
