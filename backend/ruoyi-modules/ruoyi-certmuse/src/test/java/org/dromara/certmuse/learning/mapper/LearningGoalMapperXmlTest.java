package org.dromara.certmuse.learning.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class LearningGoalMapperXmlTest {
    private static final String NAMESPACE = "org.dromara.certmuse.learning.mapper.LearningGoalMapper.";

    @Test
    void optionsUsesUndatedSyllabusOnlyAsFallbackAfterEffectiveVersions() throws Exception {
        String sql = sql("selectSelectableCertifications");

        assertFallbackSelection(sql);
    }

    @Test
    void createValidationUsesTheSameUndatedSyllabusFallbackRule() throws Exception {
        String sql = sql("selectEnabledCertificationWithSyllabus");

        assertFallbackSelection(sql);
    }

    @Test
    void batchSnapshotWrapsItsLimitedOfficialQueryBeforeTheFallbackUnion() throws Exception {
        String sql = sql("selectBatchSnapshot", Map.of(
            "today", LocalDate.of(2026, 8, 13),
            "certificationId", 9L,
            "targetExamYear", 2026,
            "targetExamMonth", 11
        ));

        assertThat(sql)
            .contains("limit 1) union all select 'estimated'")
            .doesNotContain("limit 1 union all");
    }

    private static void assertFallbackSelection(String sql) {
        assertThat(sql)
            .contains("(published_date <= ? or published_date is null)")
            .contains("order by (published_date is null) asc")
            .contains("published_date desc nulls last")
            .contains("create_time desc, id desc limit 1")
            .doesNotContain("published_date is not null");
    }

    private static String sql(String statement) throws Exception {
        return sql(statement, Map.of("today", LocalDate.of(2026, 8, 13), "certificationId", 9L));
    }

    private static String sql(String statement, Map<String, ?> parameters) throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/certmuse/LearningGoalMapper.xml";
        try (InputStream stream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement(NAMESPACE + statement)
            .getBoundSql(parameters)
            .getSql()
            .replaceAll("\\s+", " ")
            .trim()
            .toLowerCase();
    }
}
