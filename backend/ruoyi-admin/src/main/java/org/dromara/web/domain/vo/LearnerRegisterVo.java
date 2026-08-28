package org.dromara.web.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Result of a successful learner self-registration. */
@Data
@AllArgsConstructor
public class LearnerRegisterVo {

    private String username;

    private String roleKey;
}
