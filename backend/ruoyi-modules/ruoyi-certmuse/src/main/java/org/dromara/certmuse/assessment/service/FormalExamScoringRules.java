package org.dromara.certmuse.assessment.service;

import java.math.BigDecimal;

/** Downstream evidence thresholds for formal examinations. */
public final class FormalExamScoringRules {
    private static final BigDecimal MISTAKE_THRESHOLD = new BigDecimal("0.40");

    private FormalExamScoringRules() {
    }

    public static String direction(BigDecimal scoreRate) {
        return scoreRate.compareTo(MISTAKE_THRESHOLD) < 0 ? "negative" : "positive";
    }

    public static boolean isMistake(BigDecimal scoreRate) {
        return scoreRate.compareTo(MISTAKE_THRESHOLD) < 0;
    }
}
