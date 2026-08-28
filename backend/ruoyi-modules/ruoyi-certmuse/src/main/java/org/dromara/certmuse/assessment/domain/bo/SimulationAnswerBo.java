package org.dromara.certmuse.assessment.domain.bo;

import java.util.List;
import lombok.Data;

/** Frozen simulation answer; choices use value and subjective questions use text. */
@Data
public class SimulationAnswerBo {
    private List<String> value;
    private String text;
}
