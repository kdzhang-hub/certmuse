package org.dromara.certmuse.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class FormalExamScoringRulesTest {
    @Test
    void evidenceAndMistakeThresholdsMatchTheFormalExamContract() {
        assertThat(FormalExamScoringRules.direction(new BigDecimal("0.39"))).isEqualTo("negative");
        assertThat(FormalExamScoringRules.direction(new BigDecimal("0.40"))).isEqualTo("positive");
        assertThat(FormalExamScoringRules.direction(new BigDecimal("1.00"))).isEqualTo("positive");
        assertThat(FormalExamScoringRules.isMistake(new BigDecimal("0.39"))).isTrue();
        assertThat(FormalExamScoringRules.isMistake(new BigDecimal("0.40"))).isFalse();
    }
}
