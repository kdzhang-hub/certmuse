package org.dromara.certmuse.catalog.domain.bo;

import lombok.Data;

/** Qualification list filters. */
@Data
public class QualificationQueryBo {
    private String keyword;
    private String qualificationLevel;
    private String status;
    private Integer pageNum;
    private Integer pageSize;
}
