package org.dromara.certmuse.assessment.domain.vo;

import java.math.BigDecimal;

/** Direct ability facts for one knowledge point. */
public record KnowledgePracticeMasteryVo(
    BigDecimal currentDirectAbility, String profileStatus, String confidenceLevel
) {
}
