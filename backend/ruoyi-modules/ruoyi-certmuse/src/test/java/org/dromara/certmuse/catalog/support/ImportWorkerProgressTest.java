package org.dromara.certmuse.catalog.support;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ImportWorkerProgressTest {

    @Test
    void calculatesReadProgressWithTwoDecimalPlacesAndMaximum() {
        assertThat(ImportWorker.calculateReadProgress(1, 3)).isEqualByComparingTo("10.00");
        assertThat(ImportWorker.calculateReadProgress(2, 3)).isEqualByComparingTo("20.00");
        assertThat(ImportWorker.calculateReadProgress(4, 3)).isEqualByComparingTo("30.00");
    }

    @Test
    void calculatesStageProgressWithoutFloatingPointArithmetic() {
        assertThat(ImportWorker.calculateStageProgress(100, 300, 30, 50))
            .isEqualByComparingTo("36.66");
        assertThat(ImportWorker.calculateStageProgress(200, 300, 30, 50))
            .isEqualByComparingTo("43.33");
        assertThat(ImportWorker.calculateStageProgress(300, 300, 30, 50))
            .isEqualByComparingTo("50.00");
    }
}
