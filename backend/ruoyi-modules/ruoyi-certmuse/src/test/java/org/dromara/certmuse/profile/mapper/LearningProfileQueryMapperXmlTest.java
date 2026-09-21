package org.dromara.certmuse.profile.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class LearningProfileQueryMapperXmlTest {
    private static final String STATEMENT =
        "org.dromara.certmuse.profile.mapper.LearningProfileQueryMapper.selectCurrentOverallScore";

    @Test
    void scopesTheProjectionOnlyByAuthenticatedUserAndCurrentGoal() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/certmuse/LearningProfileQueryMapper.xml";
        try (InputStream stream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }

        var boundSql = configuration.getMappedStatement(STATEMENT)
            .getBoundSql(Map.of("userId", 7L));
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim().toLowerCase();

        assertThat(sql)
            .contains("from cm_user_goal g")
            .contains("join cm_exam_certification c on c.id = g.certification_id")
            .contains("left join cm_overall_profile op")
            .contains("op.user_id = g.user_id")
            .contains("op.goal_id = g.id")
            .contains("g.user_id = ?")
            .contains("g.status in ('active', 'paused')")
            .contains("limit 1")
            .doesNotContain("#{goalid}", "#{certificationid}", "#{syllabusversionid}");
        assertThat(boundSql.getParameterMappings()).singleElement()
            .satisfies(mapping -> assertThat(mapping.getProperty()).isEqualTo("userId"));
    }
}
