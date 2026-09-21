package org.dromara.certmuse.assessment.support;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Boundary examples for the frozen public initial-diagnostic profile arithmetic. */
@Tag("dev")
class DiagnosticProfileRulesBoundaryTest {

    @Test
    void timeCoefficientUsesThePublishedBandsAtTheirInclusiveBoundaries() {
        assertThat(DiagnosticProfileRules.timeCoefficient(true, 1, "valid", -1)).isEqualByComparingTo("1.00");
        assertThat(DiagnosticProfileRules.timeCoefficient(false, 25, "valid", 100)).isEqualByComparingTo("1.10");

        assertThat(DiagnosticProfileRules.timeCoefficient(true, 26, "valid", 100)).isEqualByComparingTo("1.03");
        assertThat(DiagnosticProfileRules.timeCoefficient(false, 60, "valid", 100)).isEqualByComparingTo("1.00");

        assertThat(DiagnosticProfileRules.timeCoefficient(true, 61, "valid", 100)).isEqualByComparingTo("1.00");
        assertThat(DiagnosticProfileRules.timeCoefficient(false, 100, "valid", 100)).isEqualByComparingTo("1.00");

        assertThat(DiagnosticProfileRules.timeCoefficient(true, 101, "valid", 100)).isEqualByComparingTo("0.90");
        assertThat(DiagnosticProfileRules.timeCoefficient(false, 120, "valid", 100)).isEqualByComparingTo("1.05");

        assertThat(DiagnosticProfileRules.timeCoefficient(true, 121, "valid", 100)).isEqualByComparingTo("1.00");
        assertThat(DiagnosticProfileRules.timeCoefficient(false, 121, "valid", 100)).isEqualByComparingTo("1.10");
    }

    @Test
    void evidenceIsAdjustedAroundFiftyAndBoundedToTheSupportedScale() {
        assertThat(DiagnosticProfileRules.adjustedEvidence(new BigDecimal("70"), new BigDecimal("0.5")))
            .isEqualByComparingTo("60");
        assertThat(DiagnosticProfileRules.adjustedEvidence(BigDecimal.ZERO, new BigDecimal("2")))
            .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(DiagnosticProfileRules.adjustedEvidence(new BigDecimal("100"), new BigDecimal("2")))
            .isEqualByComparingTo("100");
        assertThat(DiagnosticProfileRules.initialAbility(List.of())).isEqualByComparingTo("50.00000000");
    }

    @Test
    void confidenceAndProfileStatusFollowThePublishedLearnerFacingBands() {
        assertThat(DiagnosticProfileRules.confidence(1)).isEqualTo("low");
        assertThat(DiagnosticProfileRules.confidence(2)).isEqualTo("medium");

        assertThat(DiagnosticProfileRules.profileStatus(new BigDecimal("90"), 2, true)).isEqualTo("needs_retest");
        assertThat(DiagnosticProfileRules.profileStatus(new BigDecimal("20"), 2, false)).isEqualTo("urgent");
        assertThat(DiagnosticProfileRules.profileStatus(new BigDecimal("40"), 2, false)).isEqualTo("weak");
        assertThat(DiagnosticProfileRules.profileStatus(new BigDecimal("60"), 2, false)).isEqualTo("learning");
        assertThat(DiagnosticProfileRules.profileStatus(new BigDecimal("75"), 2, false)).isEqualTo("proficient");
    }
}
