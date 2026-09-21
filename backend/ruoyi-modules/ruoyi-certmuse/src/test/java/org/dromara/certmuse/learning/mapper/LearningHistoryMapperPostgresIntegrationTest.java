package org.dromara.certmuse.learning.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.dromara.certmuse.learning.domain.HistoryExamQuestionRow;
import org.dromara.certmuse.learning.domain.HistoryExamRow;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Executes unified exam-history mapper statements against PostgreSQL when a test URL is provided. */
@Tag("dev")
@EnabledIfEnvironmentVariable(named = "CERTMUSE_POSTGRES_TEST_URL", matches = "jdbc:postgresql:.*")
class LearningHistoryMapperPostgresIntegrationTest {
    @Test
    void executesUnifiedListAndOwnedDetailQueries() throws Exception {
        try (SqlSession session = factory().openSession(false)) {
            createFixture(session.getConnection());
            LearningHistoryMapper mapper = session.getMapper(LearningHistoryMapper.class);

            List<HistoryExamRow> rows = mapper.selectExams(10L, 20L, null, null, null, 0, 10);

            assertThat(rows).extracting(HistoryExamRow::getExamType)
                .containsExactly("SIMULATION", "PAST_PAPER", "INITIAL_DIAGNOSIS");
            assertThat(rows.get(2).getReportStatus()).isEqualTo("PROCESSING");
            assertThat(mapper.countExams(10L, 20L, "PAST_PAPER", null, null)).isEqualTo(1);
            assertThat(mapper.countInconsistentExams(10L, 20L)).isZero();
            assertThat(mapper.selectExam(10L, 20L, 102L).getTitle()).isEqualTo("模拟卷");
            assertThat(mapper.selectExam(99L, 20L, 102L)).isNull();

            List<HistoryExamQuestionRow> questions = mapper.selectExamQuestions(10L, 20L, 101L);
            assertThat(questions).singleElement().satisfies(question -> {
                assertThat(question.getQuestionId()).isEqualTo(1001L);
                assertThat(question.getPresentationSnapshot()).contains("冻结题干");
            });
        }
    }

    private static SqlSessionFactory factory() throws Exception {
        String url = System.getenv("CERTMUSE_POSTGRES_TEST_URL");
        String username = System.getenv().getOrDefault("CERTMUSE_POSTGRES_TEST_USERNAME", "");
        String password = System.getenv().getOrDefault("CERTMUSE_POSTGRES_TEST_PASSWORD", "");
        UnpooledDataSource dataSource = new UnpooledDataSource("org.postgresql.Driver", url, username, password);
        Configuration configuration = new Configuration(new Environment("postgres-test",
            new JdbcTransactionFactory(), dataSource));
        try (InputStream stream = Resources.getResourceAsStream("mapper/certmuse/LearningHistoryMapper.xml")) {
            new XMLMapperBuilder(stream, configuration, "mapper/certmuse/LearningHistoryMapper.xml",
                configuration.getSqlFragments()).parse();
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static void createFixture(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                create temporary table cm_learning_session(
                    id bigint primary key,user_id bigint,goal_id bigint,session_type varchar,status varchar,
                    collection_revision_id bigint,started_time timestamptz,submitted_time timestamptz,
                    settled_time timestamptz,timing_mode varchar,duration_seconds_snapshot integer,deadline_time timestamptz)
                """);
            statement.execute("""
                create temporary table cm_collection_revision(id bigint primary key,collection_name varchar)
                """);
            statement.execute("""
                create temporary table cm_assessment_report(
                    id bigint primary key,session_id bigint,user_id bigint,goal_id bigint,report_type varchar,
                    status varchar,total_score numeric,max_score numeric)
                """);
            statement.execute("""
                create temporary table cm_exam_subject(id bigint primary key,subject_name varchar)
                """);
            statement.execute("""
                create temporary table cm_session_question(
                    id bigint primary key,session_id bigint,question_id bigint,exam_subject_id bigint,question_order integer,
                    difficulty_snapshot varchar,presentation_snapshot jsonb,knowledge_snapshot jsonb)
                """);
            statement.execute("""
                create temporary table cm_question_attempt(
                    id bigint primary key,session_question_id bigint,user_id bigint,attempt_no integer,status varchar,
                    submitted_time timestamptz,is_skipped boolean,timer_status varchar,elapsed_seconds integer)
                """);
            statement.execute("""
                create temporary table cm_attempt_answer(
                    id bigint primary key,attempt_id bigint,grading_status varchar,score numeric,max_score numeric,score_rate numeric)
                """);
            statement.execute("insert into cm_collection_revision values(201,'真题卷'),(202,'模拟卷')");
            statement.execute("insert into cm_exam_subject values(301,'综合知识')");
            statement.execute("""
                insert into cm_learning_session values
                (100,10,20,'initial_diagnosis','settling',null,'2026-08-20 08:00+08','2026-08-20 09:00+08',null,null,null,null),
                (101,10,20,'past_paper_exam','completed',201,'2026-08-20 09:00+08','2026-08-20 10:00+08','2026-08-20 10:01+08','pausable',7200,'2026-08-20 11:00+08'),
                (102,10,20,'simulation','completed',202,'2026-08-20 10:00+08','2026-08-20 11:00+08','2026-08-20 11:01+08','standard',7200,'2026-08-20 12:00+08')
                """);
            statement.execute("""
                insert into cm_assessment_report values
                (401,101,10,20,'past_paper_exam','available',45,75),
                (402,102,10,20,'simulation','available',60,75)
                """);
            statement.execute("""
                insert into cm_session_question values
                (501,101,1001,301,1,'medium','{"schema_version":"1.0","questionType":"CHOICE","stem":"冻结题干"}',
                 '{"schema_version":"1.0","items":[]}'),
                (502,102,1002,301,1,'easy','{"schema_version":"1.0","questionType":"CHOICE","stem":"模拟题干"}',
                 '{"schema_version":"1.0","items":[]}')
                """);
            statement.execute("""
                insert into cm_question_attempt values
                (601,501,10,1,'submitted','2026-08-20 10:00+08',false,'valid',3600),
                (602,502,10,1,'submitted','2026-08-20 11:00+08',false,'valid',3600)
                """);
            statement.execute("insert into cm_attempt_answer values(701,601,'graded',1,1,1),(702,602,'graded',1,1,1)");
        }
    }
}
