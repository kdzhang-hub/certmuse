package org.dromara.certmuse.question.domain.bo;

import lombok.Data;

import java.util.List;

/**
 * 管理端题集分页查询条件。
 */
@Data
public class CollectionQueryBo {
    private String keyword;
    private String collectionType;
    private String certificationId;
    private String syllabusVersionId;
    private String status;
    /** 管理列表的展示状态：rejected/published/pending_review/draft。 */
    private List<String> statuses;
    private Integer pageNum;
    private Integer pageSize;
}
