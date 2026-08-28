package org.dromara.certmuse.question.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 驳回题集修订的请求。
 */
@Data
public class CollectionRejectBo {
    /** 审核驳回意见，会随修订保留并在退回草稿后可查询。 */
    @NotBlank @Size(max = 500)
    private String reviewOpinion;
}
