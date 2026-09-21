package org.dromara.certmuse.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.dromara.certmuse.catalog.service.impl.ExamGuidanceServiceImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.json.JsonMapper;

@Tag("dev")
class ExamGuidanceServiceImplTest {
    @Test
    void listRegionsWithoutFiltersUsesNoUntypedNullSqlParameters() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        ExamGuidanceServiceImpl service = new ExamGuidanceServiceImpl(jdbc, JsonMapper.builder().build());

        Map<String, Object> result = service.listRegions(null, null, null, 1, 20);

        assertThat(result).containsEntry("total", 0L).containsEntry("rows", List.of());
        assertThat(jdbc.sql).allSatisfy(sql -> assertThat(sql).doesNotContain("? is null"));
        assertThat(jdbc.arguments).hasSize(2);
        assertThat(jdbc.arguments.get(0)).isEmpty();
        assertThat(jdbc.arguments.get(1)).containsExactly(20, 0);
    }

    @Test
    void qualificationContextDoesNotExcludeMiddleOrLowQualifications() {
        ContextJdbcTemplate jdbc = new ContextJdbcTemplate();
        ExamGuidanceServiceImpl service = new ExamGuidanceServiceImpl(jdbc, JsonMapper.builder().build());

        Map<String, Object> result = service.qualificationContext(2026, "H2");

        assertThat(result).containsEntry("availability", "ENDED");
        assertThat(jdbc.sql).anySatisfy(sql -> assertThat(sql)
            .contains("c.status='0'")
            .doesNotContain("qualification_level='HIGH'"));
    }

    @Test
    void publicQualificationsOnlySelectsFuturePublishedOfficialSchedules() {
        QualificationJdbcTemplate jdbc = new QualificationJdbcTemplate();
        ExamGuidanceServiceImpl service = new ExamGuidanceServiceImpl(jdbc, JsonMapper.builder().build());

        service.publicQualifications();

        assertThat(jdbc.statement)
            .contains("p.status='published'")
            .contains("p.record_origin='official'")
            .contains("s.exam_start_date>=?")
            .contains("order by s.exam_start_date,s.id limit 1");
        assertThat(jdbc.arguments).singleElement().isInstanceOf(java.time.LocalDate.class);
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<String> sql = new ArrayList<>();
        private final List<Object[]> arguments = new ArrayList<>();

        @Override
        public <T> T queryForObject(String statement, Class<T> requiredType, Object... args) {
            sql.add(statement);
            arguments.add(args);
            return requiredType.cast(0L);
        }

        @Override
        public List<Map<String, Object>> queryForList(String statement, Object... args) {
            sql.add(statement);
            arguments.add(args);
            return List.of();
        }
    }

    private static final class ContextJdbcTemplate extends JdbcTemplate {
        private final List<String> sql = new ArrayList<>();

        @Override
        public List<Map<String, Object>> queryForList(String statement, Object... args) {
            sql.add(statement);
            if (statement.startsWith("select * from cm_exam_period")) {
                return List.of(Map.of("id", 1L, "period_code", "2026-H2"));
            }
            return List.of();
        }
    }

    private static final class QualificationJdbcTemplate extends JdbcTemplate {
        private String statement;
        private Object[] arguments;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            statement = sql;
            arguments = args;
            return List.of();
        }
    }
}
