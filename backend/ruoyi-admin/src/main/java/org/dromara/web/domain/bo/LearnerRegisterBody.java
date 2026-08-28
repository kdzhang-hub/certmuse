package org.dromara.web.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/** Public learner self-registration request. */
@Data
public class LearnerRegisterBody {

    @NotBlank(message = "{user.username.not.blank}")
    @Length(min = 2, max = 30, message = "{user.username.length.valid}")
    private String username;

    @NotBlank(message = "{user.password.not.blank}")
    @Length(min = 8, max = 30, message = "密码长度必须在8到30个字符之间")
    private String password;

    private String code;

    private String uuid;

    @NotBlank(message = "客户端不能为空")
    private String clientId;

    @NotBlank(message = "授权类型不能为空")
    private String grantType;
}
