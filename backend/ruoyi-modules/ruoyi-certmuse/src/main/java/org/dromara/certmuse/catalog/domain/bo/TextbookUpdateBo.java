package org.dromara.certmuse.catalog.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Editable metadata for a draft or published textbook. */
@Data
public class TextbookUpdateBo {
    @NotBlank(message = "教材名称不能为空")
    private String title;

    @NotBlank(message = "绑定资格不能为空")
    private String certificationId;
}
