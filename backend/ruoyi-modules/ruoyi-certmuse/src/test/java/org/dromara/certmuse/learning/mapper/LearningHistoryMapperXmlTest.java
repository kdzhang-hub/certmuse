package org.dromara.certmuse.learning.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class LearningHistoryMapperXmlTest {
    private static final String NAMESPACE = "org.dromara.certmuse.learning.mapper.LearningHistoryMapper.";

    @Test
    void allStatementsParseAndOwnedDetailQueriesBindUserAndGoal() throws Exception {
        Configuration configuration = configuration();

        assertThat(sql(configuration, "selectTask", Map.of("userId", 1L, "goalId", 2L, "taskId", 3L)))
            .contains("t.user_id=?", "t.goal_id=?", "x.task_id=?");
        assertThat(sql(configuration, "selectQuestionAttempts", Map.of("userId", 1L, "goalId", 2L, "questionId", 3L)))
            .contains("s.user_id=?", "s.goal_id=?", "sq.question_id=?");
    }

    @Test
    void goalsQueryUsesAnExplicitUserIdParameter() throws Exception {
        Param parameter = LearningHistoryMapper.class.getMethod("selectGoals", long.class)
            .getParameters()[0].getAnnotation(Param.class);

        assertThat(parameter).isNotNull();
        assertThat(parameter.value()).isEqualTo("userId");
        assertThat(sql(configuration(), "selectGoals", Map.of("userId", 1L))).contains("s.user_id=?");
    }

    @Test
    void examQueriesUseCompletedOwnedSessionsAndFrozenFacts() throws Exception {
        Configuration configuration = configuration();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userId", 1L); parameters.put("goalId", 2L); parameters.put("sessionId", 3L);
        parameters.put("examType", null); parameters.put("from", null); parameters.put("to", null);
        parameters.put("offset", 0); parameters.put("limit", 10);

        assertThat(sql(configuration, "selectExams", parameters))
            .contains("s.session_type='initial_diagnosis'", "s.session_type in ('past_paper_exam','simulation')",
                "s.user_id=?", "s.goal_id=?", "order by x.completed_at desc,x.session_id desc", "offset ? limit ?");
        assertThat(sql(configuration, "selectExam", parameters))
            .contains("s.user_id=?", "s.goal_id=?", "x.session_id=?");
        assertThat(sql(configuration, "selectExamQuestions", parameters))
            .contains("s.id=?", "s.user_id=?", "s.goal_id=?", "sq.presentation_snapshot::text",
                "sq.grading_snapshot::text", "answer.answer_data::text", "order by sq.question_order,sq.id");
        parameters.put("sessionType", null);
        assertThat(sql(configuration, "selectPractices", parameters))
            .contains("s.user_id=?", "s.goal_id=?", "s.session_type in ('self_practice','past_paper_practice')",
                "s.status='completed'", "s.settled_time is not null", "offset ? limit ?");
        assertThat(sql(configuration, "selectPracticeItems", parameters))
            .contains("s.id=?", "s.user_id=?", "s.goal_id=?", "sq.grading_snapshot::text",
                "order by sq.question_order,sq.id");
    }

    @Test
    void aggregateQueriesPageAfterGroupingAndExcludeDrafts() throws Exception {
        Configuration configuration = configuration();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userId", 1L); parameters.put("goalId", 2L); parameters.put("keyword", null);
        parameters.put("subjectId", null); parameters.put("knowledgePointId", null);
        parameters.put("questionType", null); parameters.put("difficulty", null);
        parameters.put("correctionStatus", null); parameters.put("from", OffsetDateTime.now());
        parameters.put("to", null); parameters.put("offset", 0); parameters.put("limit", 10);

        assertThat(sql(configuration, "selectQuestions", parameters))
            .contains("a.status in ('submitted','graded')", "count(*) over(partition by question_id)", "offset ? limit ?");
        assertThat(sql(configuration, "selectTasks", parameters))
            .contains("having count(distinct ti.id)", "s.status='completed'", "offset ? limit ?")
            .doesNotContain("knowledge_point_name");
        assertThat(sql(configuration, "countInconsistentQuestions", parameters))
            .contains("x.error_count>0", "x.pending_error_count=0", "x.successful_correction_count=0")
            .doesNotContain("#{correctionstatus}");
    }

    @Test
    void nullOptionalFiltersAreOmittedInsteadOfBoundAsUntypedParameters() throws Exception {
        Configuration configuration = configuration();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userId", 1L); parameters.put("goalId", 2L);
        parameters.put("keyword", null); parameters.put("subjectId", null); parameters.put("knowledgePointId", null);
        parameters.put("questionType", null); parameters.put("difficulty", null); parameters.put("correctionStatus", null);
        parameters.put("examType", null); parameters.put("from", null); parameters.put("to", null);
        parameters.put("offset", 0); parameters.put("limit", 10);

        assertThat(parameterProperties(configuration, "selectTasks", parameters))
            .doesNotContain("keyword", "subjectId", "knowledgePointId", "from", "to");
        assertThat(parameterProperties(configuration, "selectQuestions", parameters))
            .doesNotContain("keyword", "questionType", "difficulty", "knowledgePointId", "correctionStatus", "from", "to");
        assertThat(parameterProperties(configuration, "selectExams", parameters))
            .doesNotContain("examType", "from", "to");
    }

    private static Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/certmuse/LearningHistoryMapper.xml";
        try (InputStream stream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private static String sql(Configuration configuration, String statement, Map<String, ?> parameters) {
        return configuration.getMappedStatement(NAMESPACE + statement).getBoundSql(parameters).getSql()
            .replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private static List<String> parameterProperties(Configuration configuration, String statement,
                                                    Map<String, ?> parameters) {
        return configuration.getMappedStatement(NAMESPACE + statement).getBoundSql(parameters)
            .getParameterMappings().stream().map(ParameterMapping::getProperty).toList();
    }
}
