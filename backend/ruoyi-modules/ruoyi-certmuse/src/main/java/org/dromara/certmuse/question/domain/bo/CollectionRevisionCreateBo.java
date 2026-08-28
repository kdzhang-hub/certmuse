package org.dromara.certmuse.question.domain.bo;

import lombok.Data;

/**
 * 创建题集新修订的请求。
 */
@Data
public class CollectionRevisionCreateBo {
    /** 同一题集的历史修订；省略时复制当前已发布修订。 */
    private String sourceRevisionId;
}
