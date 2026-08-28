package org.dromara.certmuse.question.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改题集稳定名称的请求。
 */
@Data
public class CollectionRenameBo {
    @NotBlank
    @Size(max = 200)
    private String collectionName;
}
