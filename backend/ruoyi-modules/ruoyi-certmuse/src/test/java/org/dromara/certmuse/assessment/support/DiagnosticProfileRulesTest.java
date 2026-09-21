package org.dromara.certmuse.assessment.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class DiagnosticProfileRulesTest {

    @Test
    void calculatesFrozenMediumInitialVectors() {
        assertThat(DiagnosticProfileRules.initialAbility(List.of(new BigDecimal("85")))).isEqualByComparingTo("58.75000000");
        assertThat(DiagnosticProfileRules.initialAbility(List.of(new BigDecimal("25")))).isEqualByComparingTo("43.75000000");
        assertThat(DiagnosticProfileRules.initialAbility(List.of(new BigDecimal("85"), new BigDecimal("85")))).isEqualByComparingTo("64.00000000");
    }

    @Test
    void invalidOrMissingTimerNeverChangesEvidence() {
        assertThat(DiagnosticProfileRules.timeCoefficient(true, 8, "invalid", 40)).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(DiagnosticProfileRules.timeCoefficient(false, null, "missing", 40)).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(DiagnosticProfileRules.timeCoefficient(true, 8, "valid", 40)).isEqualByComparingTo("1.05");
    }

    @Test
    void oneQuestionCannotUseScoreBandStatus() {
        assertThat(DiagnosticProfileRules.profileStatus(new BigDecimal("90"), 1, false)).isEqualTo("provisional");
        assertThat(DiagnosticProfileRules.profileStatus(new BigDecimal("90"), 2, false)).isEqualTo("mastery_candidate");
    }
}
