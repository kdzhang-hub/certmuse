package org.dromara.certmuse.question.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 新建题集或保存草稿的内容请求。
 */
@Data
public class CollectionSaveBo {
    @NotBlank @Size(max = 200)
    private String collectionName;
    @NotBlank
    private String collectionType;
    @NotBlank
    private String certificationId;
    @NotBlank
    private String syllabusVersionId;
    @NotNull @Min(1)
    private Integer durationMinutes;
    /** Required only for a PAST_PAPER collection; never inferred from the display name. */
    private Integer examYear;
    private Integer examMonth;
    private String paperTypeCode;
    private String paperTypeName;
    /** 保存已有草稿时必须提交的乐观锁版本，不等同于业务修订号。 */
    private String rowVersion;
    @NotNull @Valid
    private List<ItemBo> items;

    @Data
    public static class ItemBo {
        @NotNull @Min(1)
        private Integer itemOrder;
        @NotBlank
        private String questionRevisionId;
        @NotNull @DecimalMin(value = "0", inclusive = false)
        private BigDecimal reportScore;
    }
}
