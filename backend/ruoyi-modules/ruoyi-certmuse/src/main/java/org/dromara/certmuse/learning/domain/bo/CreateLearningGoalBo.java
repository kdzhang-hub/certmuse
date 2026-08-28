package org.dromara.certmuse.learning.domain.bo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Learner supplied fields for the first learning-goal creation. */
@Data
public class CreateLearningGoalBo {

    @NotBlank(message = "资格不能为空")
    @Pattern(regexp = "[1-9]\\d*", message = "资格ID格式不正确")
    private String certificationId;

    @NotNull(message = "目标考试年份不能为空")
    @Min(value = 1, message = "目标考试年份格式不正确")
    private Integer targetExamYear;

    @NotNull(message = "目标考试月份不能为空")
    private Integer targetExamMonth;

    @NotNull(message = "每日学习时长不能为空")
    @Min(value = 1, message = "每日学习时长格式不正确")
    @Max(value = 10000, message = "每日学习时长格式不正确")
    private Integer dailyMinutes;

    /** Rejects properties outside the frozen U03 request contract. */
    @JsonAnySetter
    public void rejectUnknownProperty(String property, Object value) {
        throw new IllegalArgumentException("请求包含不支持的字段: " + property);
    }
}
