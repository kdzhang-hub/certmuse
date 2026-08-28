package org.dromara.certmuse.learning.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Query parameters for the learner task pool. */
@Data
public class LearningTaskQueryBo {
    @Size(max = 100, message = "关键词不能超过100个字符")
    @Pattern(regexp = "(?s).*\\S.*", message = "关键词不能为空白")
    private String keyword;

    @Min(value = 1, message = "页码最小为1")
    private int pageNum = 1;

    @Min(value = 1, message = "每页数量最小为1")
    @Max(value = 100, message = "每页数量最大为100")
    private int pageSize = 10;
}
