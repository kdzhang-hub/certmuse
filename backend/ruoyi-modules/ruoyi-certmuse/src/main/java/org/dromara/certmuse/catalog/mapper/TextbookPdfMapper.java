package org.dromara.certmuse.catalog.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.catalog.domain.TextbookPdfRow;
import org.dromara.certmuse.catalog.domain.vo.LearningTextbookPdfListItemVo;
import org.dromara.certmuse.catalog.domain.vo.PublicTextbookCertificationVo;
import org.dromara.certmuse.catalog.domain.vo.PublicTextbookPdfListItemVo;

@Mapper
public interface TextbookPdfMapper {
    TextbookPdfRow selectAdmin(@Param("documentId") long documentId, @Param("visibleUserId") Long visibleUserId);
    TextbookPdfRow lockAdmin(@Param("documentId") long documentId, @Param("visibleUserId") Long visibleUserId);
    TextbookPdfRow selectLearner(@Param("userId") long userId, @Param("documentId") long documentId);
    List<LearningTextbookPdfListItemVo> selectLearnerList(@Param("userId") long userId, @Param("keyword") String keyword);
    List<PublicTextbookCertificationVo> selectPublicCertifications();
    List<PublicTextbookPdfListItemVo> selectPublicList(@Param("certificationId") long certificationId,
                                                        @Param("keyword") String keyword);
    TextbookPdfRow selectPublic(@Param("documentId") long documentId);
    int upsert(@Param("id") long id, @Param("documentId") long documentId, @Param("objectKey") String objectKey,
               @Param("fileName") String fileName, @Param("fileSize") long fileSize, @Param("fileHash") String fileHash,
               @Param("userId") long userId);
    int delete(@Param("documentId") long documentId);
}
