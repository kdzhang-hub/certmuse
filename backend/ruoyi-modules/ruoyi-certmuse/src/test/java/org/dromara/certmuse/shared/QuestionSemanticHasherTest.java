package org.dromara.certmuse.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class QuestionSemanticHasherTest {

    @Test
    void ignoresChoiceOrderAndFormattingOnlyDifferences() {
        String first = QuestionSemanticHasher.hash("CHOICE", "题干\n内容", List.of("甲", "乙"));
        String second = QuestionSemanticHasher.hash("CHOICE", "  题干   内容 ", List.of("乙", "甲"));

        assertThat(second).isEqualTo(first);
    }

    @Test
    void ignoresSubjectiveAnswersByHashingOnlyTheirStem() {
        assertThat(QuestionSemanticHasher.hash("CASE", "案例题", List.of()))
            .isEqualTo(QuestionSemanticHasher.hash("CASE", " 案例题 ", List.of("不参与计算")));
    }

    @Test
    void distinguishesAChangedChoiceText() {
        assertThat(QuestionSemanticHasher.hash("CHOICE", "题干", List.of("甲", "乙")))
            .isNotEqualTo(QuestionSemanticHasher.hash("CHOICE", "题干", List.of("甲", "丙")));
    }
}
