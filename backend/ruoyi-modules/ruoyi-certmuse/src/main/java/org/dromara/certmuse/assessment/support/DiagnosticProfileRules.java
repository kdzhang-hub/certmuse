package org.dromara.certmuse.assessment.support;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collection;

/** Immutable V8 arithmetic used by the initial-diagnostic result worker. */
public final class DiagnosticProfileRules {
    private static final BigDecimal FIFTY = BigDecimal.valueOf(50);
    private static final MathContext DECIMAL128 = MathContext.DECIMAL128;

    private DiagnosticProfileRules() { }

    public static BigDecimal initialAbility(Collection<BigDecimal> adjustedEvidence) {
        BigDecimal sum = adjustedEvidence.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return FIFTY.multiply(BigDecimal.valueOf(3)).add(sum)
            .divide(BigDecimal.valueOf(3L + adjustedEvidence.size()), 8, RoundingMode.HALF_EVEN);
    }

    public static BigDecimal adjustedEvidence(BigDecimal raw, BigDecimal combinedCoefficient) {
        return FIFTY.add(combinedCoefficient.multiply(raw.subtract(FIFTY), DECIMAL128), DECIMAL128)
            .max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
    }

    public static BigDecimal timeCoefficient(boolean correct, Integer elapsedSeconds, String timerStatus, int standardSeconds) {
        if (elapsedSeconds == null || !"valid".equals(timerStatus) || standardSeconds <= 0) return BigDecimal.ONE;
        if (elapsedSeconds <= standardSeconds / 4) return correct ? new BigDecimal("1.05") : new BigDecimal("1.10");
        if (BigDecimal.valueOf(elapsedSeconds).compareTo(BigDecimal.valueOf(standardSeconds).multiply(new BigDecimal("0.60"))) <= 0)
            return correct ? new BigDecimal("1.03") : BigDecimal.ONE;
        if (elapsedSeconds <= standardSeconds) return BigDecimal.ONE;
        if (elapsedSeconds <= Math.round(standardSeconds * 1.2d)) return correct ? new BigDecimal("0.90") : new BigDecimal("1.05");
        return correct ? BigDecimal.ONE : new BigDecimal("1.10");
    }

    public static String confidence(int distinctQuestionCount) {
        return distinctQuestionCount <= 1 ? "low" : "medium";
    }

    public static String profileStatus(BigDecimal ability, int distinctQuestionCount, boolean conflict) {
        if (conflict) return "needs_retest";
        if (distinctQuestionCount <= 1) return "provisional";
        if (ability.compareTo(BigDecimal.valueOf(40)) < 0) return "urgent";
        if (ability.compareTo(BigDecimal.valueOf(60)) < 0) return "weak";
        if (ability.compareTo(BigDecimal.valueOf(75)) < 0) return "learning";
        if (ability.compareTo(BigDecimal.valueOf(85)) < 0) return "proficient";
        return "mastery_candidate";
    }
}
