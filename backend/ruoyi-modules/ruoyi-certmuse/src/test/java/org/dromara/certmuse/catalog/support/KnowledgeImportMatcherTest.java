package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.catalog.domain.KnowledgeImportMatch;
import org.dromara.certmuse.catalog.domain.KnowledgeImportPointRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class KnowledgeImportMatcherTest {
    private final KnowledgeImportMatcher matcher = new KnowledgeImportMatcher(new HungarianMatcher());

    @Test
    void neverMatchesAcrossSubjects() {
        KnowledgeImportPointRow incoming = point(null, 1L, "2", "数据库系统", null);
        KnowledgeImportPointRow wrongSubject = point(10L, 2L, "2", "数据库系统", null);

        KnowledgeImportMatch result = matcher.match(List.of(incoming), List.of(wrongSubject)).getFirst();

        assertThat(result.existing()).isNull();
    }

    @Test
    void titleAndStructureCanPreserveIdentityWhenNumbersShift() {
        KnowledgeImportPointRow incoming = point(null, 1L, "1.2", "数据库系统", "基础知识");
        KnowledgeImportPointRow existing = point(10L, 1L, "1.1", "数据库系统", "基础知识");

        KnowledgeImportMatch result = matcher.match(List.of(incoming), List.of(existing)).getFirst();

        assertThat(result.existing().getKnowledgePointId()).isEqualTo(10L);
        assertThat(result.titleScore()).isEqualTo(4500);
        assertThat(result.parentScore()).isEqualTo(2500);
        assertThat(result.totalScore()).isGreaterThan(9000);
        assertThat(result.numberScore()).isLessThan(500);
    }

    @Test
    void leavesIncomingPointsUnmatchedWhenThereIsNoCandidate() {
        KnowledgeImportMatch result = matcher.match(
            List.of(point(null, 1L, "9.9", "新知识点", null)), List.of()
        ).getFirst();

        assertThat(result.existing()).isNull();
        assertThat(result.totalScore()).isZero();
        assertThat(result.ambiguous()).isFalse();
    }

    @Test
    void matchesChildrenAfterTheirParentsAndKeepsTheHierarchyExplainable() {
        KnowledgeImportPointRow incomingRoot = point(null, 1L, "1", "数据库", null);
        KnowledgeImportPointRow incomingChild = child(null, 1L, "1.1", "关系模型", "1", "数据库");
        KnowledgeImportPointRow existingRoot = point(10L, 1L, "0.9", "数据库", null);
        KnowledgeImportPointRow existingChild = child(11L, 1L, "0.9.1", "关系模型", "0.9", "数据库");

        var results = matcher.match(
            List.of(incomingRoot, incomingChild), List.of(existingRoot, existingChild)
        );

        assertThat(results.get(0).existing().getKnowledgePointId()).isEqualTo(10L);
        assertThat(results.get(1).existing().getKnowledgePointId()).isEqualTo(11L);
        assertThat(results.get(1).possibleMove()).isTrue();
    }

    @Test
    void marksEquivalentCandidatesAsAmbiguousInsteadOfPretendingThereIsAUniqueMatch() {
        KnowledgeImportPointRow incoming = point(null, 1L, "1", "相同标题", null);
        KnowledgeImportPointRow first = point(10L, 1L, "1", "相同标题", null);
        KnowledgeImportPointRow second = point(11L, 1L, "1", "相同标题", null);

        KnowledgeImportMatch result = matcher.match(List.of(incoming), List.of(first, second)).getFirst();

        assertThat(result.existing()).isNotNull();
        assertThat(result.candidateGap()).isZero();
        assertThat(result.ambiguous()).isTrue();
    }

    @Test
    void normalizesNullAndChinesePunctuationBeforeScoringText() {
        KnowledgeImportPointRow incoming = point(null, 1L, "1", " 数据库，系统 ", null);
        KnowledgeImportPointRow existing = point(10L, 1L, "1", "数据库 系统", null);

        KnowledgeImportMatch result = matcher.match(List.of(incoming), List.of(existing)).getFirst();

        assertThat(result.existing()).isSameAs(existing);
        assertThat(result.titleScore()).isGreaterThan(3000);
    }

    @Test
    void doesNotTreatWeaklySimilarNodesAsTheSameIdentity() {
        KnowledgeImportPointRow incoming = point(null, 1L, "1.1.2.2.5", "多核处理器与并行计算", "处理器基础");
        KnowledgeImportPointRow existing = point(10L, 1L, "1.1.2.5.1", "显示、音频、网络和SATA等", "外部设备");

        KnowledgeImportMatch result = matcher.match(List.of(incoming), List.of(existing)).getFirst();

        assertThat(result.existing()).isNull();
        assertThat(result.totalScore()).isZero();
    }

    private static KnowledgeImportPointRow point(
        Long id, long subjectId, String number, String title, String parentTitle
    ) {
        KnowledgeImportPointRow row = new KnowledgeImportPointRow();
        row.setKnowledgePointId(id); row.setExamSubjectId(subjectId); row.setSyllabusNumber(number);
        row.setSyllabusTitle(title); row.setParentSyllabusTitle(parentTitle); row.setTreeDepth(1);
        row.setSortOrder(1); row.setStatus("0");
        return row;
    }

    private static KnowledgeImportPointRow child(
        Long id, long subjectId, String number, String title, String parentNumber, String parentTitle
    ) {
        KnowledgeImportPointRow row = point(id, subjectId, number, title, parentTitle);
        row.setParentSyllabusNumber(parentNumber);
        return row;
    }
}
