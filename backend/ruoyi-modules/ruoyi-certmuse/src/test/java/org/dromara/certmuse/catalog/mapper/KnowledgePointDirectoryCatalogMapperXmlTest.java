package org.dromara.certmuse.catalog.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class KnowledgePointDirectoryCatalogMapperXmlTest {

    @Test
    void appliesCurrentGoalAndCatalogVisibilityInOneQuery() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/certmuse/KnowledgePointDirectoryCatalogMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        String statement = "org.dromara.certmuse.catalog.mapper.KnowledgePointDirectoryCatalogMapper.selectForCurrentGoal";
        assertThat(configuration.hasStatement(statement)).isTrue();
        String sql = normalize(configuration.getMappedStatement(statement).getBoundSql(Map.of(
            "userId", 7L, "ids", List.of(1L, 2L))).getSql());

        assertThat(sql).contains("from cm_user_goal g")
            .contains("g.status in ('active', 'paused')")
            .contains("join cm_knowledge_point kp")
            .contains("kp.syllabus_version_id = goal.syllabus_version_id")
            .contains("kp.del_flag = '0'")
            .contains("kp.status = '0'")
            .contains("join cm_exam_subject es")
            .contains("es.certification_id = goal.certification_id")
            .contains("join cm_syllabus_version sv")
            .contains("sv.certification_id = goal.certification_id")
            .contains("kp.id in ( ? , ? )")
            .doesNotContain("parent_id")
            .doesNotContain("tree_depth")
            .doesNotContain("importance")
            .doesNotContain("row_version");
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
