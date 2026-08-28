package org.dromara.certmuse.catalog.mapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.catalog.domain.vo.KnowledgeSubjectVo;
import org.dromara.certmuse.catalog.domain.vo.KnowledgeTreeNodeVo;
import org.dromara.certmuse.catalog.domain.vo.KnowledgeTreeSummaryVo;
import org.dromara.certmuse.catalog.domain.vo.LatestKnowledgeImportVo;
import org.dromara.certmuse.catalog.domain.vo.SyllabusListVo;
import org.dromara.certmuse.catalog.domain.vo.SyllabusOverviewBaseVo;
import org.dromara.certmuse.catalog.domain.CmIdempotencyRecord;

@Mapper
public interface KnowledgeTreeMapper {

    List<SyllabusListVo> selectSyllabuses(@Param("keyword") String keyword,
                                          @Param("certificationId") Long certificationId,
                                          @Param("versionName") String versionName,
                                          @Param("status") String status,
                                          @Param("limit") int limit,
                                          @Param("offset") long offset);

    long countSyllabuses(@Param("keyword") String keyword,
                         @Param("certificationId") Long certificationId,
                         @Param("versionName") String versionName,
                         @Param("status") String status);

    SyllabusOverviewBaseVo selectOverview(@Param("syllabusVersionId") long syllabusVersionId);

    LatestKnowledgeImportVo selectLatestKnowledgeImport(@Param("syllabusVersionId") long syllabusVersionId);

    KnowledgeTreeSummaryVo selectSummary(@Param("syllabusVersionId") long syllabusVersionId);

    List<KnowledgeSubjectVo> selectSubjects(@Param("syllabusVersionId") long syllabusVersionId);

    List<KnowledgeTreeNodeVo> selectNodes(@Param("syllabusVersionId") long syllabusVersionId);

    Long lockSyllabusVersion(@Param("syllabusVersionId") long syllabusVersionId);

    int updateSyllabusPublishedDate(@Param("syllabusVersionId") long syllabusVersionId,
                                    @Param("publishedDate") LocalDate publishedDate,
                                    @Param("userId") Long userId);

    CmIdempotencyRecord selectIdempotency(@Param("action") String action, @Param("requestId") String requestId);

    int insertIdempotency(@Param("id") long id, @Param("action") String action, @Param("requestId") String requestId,
                          @Param("payloadHash") String payloadHash, @Param("resourceId") long resourceId,
                          @Param("expiresTime") OffsetDateTime expiresTime);

    int completeIdempotency(@Param("action") String action, @Param("requestId") String requestId,
                            @Param("resourceId") long resourceId, @Param("responseBody") String responseBody);

    long countImportingImports(@Param("syllabusVersionId") long syllabusVersionId);

    String setQualificationCascadeDelete(@Param("enabled") boolean enabled);

    List<String> selectSourceObjectKeysBySyllabusVersion(@Param("syllabusVersionId") long syllabusVersionId);

    List<String> selectImageObjectKeysBySyllabusVersion(@Param("syllabusVersionId") long syllabusVersionId);

    int deleteKnowledgeImportDiffsBySyllabusVersion(@Param("syllabusVersionId") long syllabusVersionId);

    int deleteQuestionsExclusiveToSyllabusVersion(@Param("syllabusVersionId") long syllabusVersionId);

    int deleteSyllabusVersion(@Param("syllabusVersionId") long syllabusVersionId);
}
