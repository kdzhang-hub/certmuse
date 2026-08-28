package org.dromara.certmuse.learning.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

/** Request for freezing selected pending mistakes into one correction session. */
@Data
public class CreateMistakeCorrectionSessionBo {
    @NotEmpty
    private List<String> questionIds;
}
