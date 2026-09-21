package org.dromara.certmuse.catalog.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class HungarianMatcherTest {
    private final HungarianMatcher matcher = new HungarianMatcher();

    @Test
    void choosesGlobalMaximumInsteadOfGreedyMaximum() {
        assertThat(matcher.maximize(new int[][]{{9000, 8000}, {8000, 0}}))
            .containsExactly(1, 0);
    }

    @Test
    void supportsMoreIncomingRowsThanExistingColumns() {
        int[] assignment = matcher.maximize(new int[][]{{100}, {90}, {80}});
        assertThat(assignment).containsExactly(0, -1, -1);
    }
}
