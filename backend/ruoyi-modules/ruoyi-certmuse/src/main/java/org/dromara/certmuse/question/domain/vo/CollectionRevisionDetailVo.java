package org.dromara.certmuse.question.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 单个题集修订的完整详情。
 */
public record CollectionRevisionDetailVo(
    String revisionId, String collectionId, String collectionCode, int revisionNo,
    String collectionName, String collectionType,
    String certificationId, String certificationName,
    String syllabusVersionId, String syllabusVersionName,
    Integer durationMinutes, boolean pauseAllowed, String status,
    int questionCount, BigDecimal totalReportScore,
    /** 草稿保存使用的乐观锁版本，不等同于业务修订号。 */ String rowVersion,
    /** 审核驳回时记录的审核意见。 */ String reviewOpinion,
    List<String> allowedActions, List<ItemVo> items, OffsetDateTime updatedTime
) {
    public record ItemVo(
        int itemOrder, String questionRevisionId, String questionId, String questionCode,
        String stem, String questionType, String difficulty, String questionStatus,
        String examSubjectId, String examSubjectName,
        String knowledgePointId, String knowledgePointLabel, BigDecimal reportScore
    ) { }
}
