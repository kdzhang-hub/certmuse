package org.dromara.certmuse.catalog.domain.bo;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class TextbookQueryBo {
    private String title;
    private String certificationId;
    private String syllabusVersionId;
    private String edition;
    private String status;
    private String createBy;
    private OffsetDateTime beginCreateTime;
    private OffsetDateTime endCreateTime;
    private String orderByColumn;
    private String isAsc;
    private Integer pageNum;
    private Integer pageSize;
}
