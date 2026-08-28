package org.dromara.certmuse.learning.domain.bo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Learner confirmation payload for switching to another certification. */
@Data
public class SwitchLearningGoalBo {
    @NotBlank(message = "资格不能为空")
    @Pattern(regexp = "[1-9]\\d*", message = "资格ID格式不正确")
    private String certificationId;
    @NotNull(message = "目标考试年份不能为空") @Min(1) private Integer targetExamYear;
    @NotNull(message = "目标考试月份不能为空") private Integer targetExamMonth;
    @NotNull(message = "当前目标版本不能为空") @Min(0) private Long expectedCurrentGoalVersion;
    @NotNull(message = "确认标记不能为空") private Boolean confirmAbandonInProgress;

    @JsonAnySetter
    public void rejectUnknownProperty(String property, Object value) {
        throw new IllegalArgumentException("请求包含不支持的字段: " + property);
    }
}
