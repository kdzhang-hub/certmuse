package org.dromara.certmuse.catalog.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * Completed import batch list query.
 */
@Data
public class CompletedImportBatchQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String keyword;

    private String importType;

    @Pattern(regexp = "^[1-9][0-9]{0,18}$", message = "考纲版本ID必须是十进制正整数")
    private String syllabusVersionId;

    @Pattern(regexp = "^[1-9][0-9]{0,18}$", message = "上传人ID必须是十进制正整数")
    private String uploaderId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate completedStartDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate completedEndDate;

    @Min(value = 1, message = "页码最小为1")
    private Integer pageNum;

    @Min(value = 1, message = "每页数量最小为1")
    @Max(value = 100, message = "每页数量最大为100")
    private Integer pageSize;

    private String orderByColumn;

    private String isAsc;
}
