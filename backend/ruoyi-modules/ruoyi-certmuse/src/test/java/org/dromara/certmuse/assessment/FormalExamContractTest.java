package org.dromara.certmuse.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class FormalExamContractTest {
    private static final String NAMESPACE = "org.dromara.certmuse.assessment.mapper.FormalExamMapper.";

    @Test
    void mapperLocksSessionsBeforeAggregatingAndFreezesOnlyPublishedQuestions() throws Exception {
        Configuration configuration = mapperConfiguration();
        String lockSql = sql(configuration, "lockSession", Map.of(
            "sessionId", 1L, "userId", 2L, "sessionType", "simulation"));
        String workerSql = sql(configuration, "lockSessionForWorker", Map.of("sessionId", 1L));
        String questionSql = sql(configuration, "selectCurrentQuestions", Map.of(
            "collectionId", 1L, "collectionType", "SIMULATION"));

        assertThat(lockSql).contains("with locked_session as materialized", "for update",
            "join cm_learning_session s on s.id=locked.id", "s.formal_attempt_no");
        assertThat(workerSql).contains("with locked_session as materialized", "for update");
        assertThat(questionSql).contains("qr.status='published'", "q.del_flag='0'", "order by ci.item_order");
    }

    @Test
    void mapperPersistsDraftsLeasesAiGradingAndSevenDayEvidenceWindow() throws Exception {
        Configuration configuration = mapperConfiguration();
        assertThat(sql(configuration, "insertDraftAttempt", Map.of(
            "attemptId", 1L, "answerId", 2L, "sessionQuestionId", 3L, "userId", 4L,
            "requestId", "request", "answerData", "{}")))
            .contains("insert into cm_attempt_answer", "grading_source", "grading_revision_no");
        assertThat(sql(configuration, "selectActiveSession", Map.of(
            "userId", 1L, "goalId", 2L, "sessionType", "past_paper_exam")))
            .contains("s.user_id=?", "s.goal_id=?", "s.session_type=?")
            .doesNotContain("s.collection_revision_id=?");
        assertThat(sql(configuration, "gradeAnswer", Map.of(
            "answerId", 1L, "score", "10", "maxScore", "20", "scoreRate", "0.5",
            "gradingSource", "AI", "gradingResult", "{}")))
            .contains("grading_status='graded'", "grading_source=?", "grading_result=?::jsonb", "grading_revision_no=1");
        assertThat(sql(configuration, "failAnswer", Map.of("answerId", 1L, "maxScore", "20", "gradingResult", "{}")))
            .contains("score=0", "score_rate=0", "grading_status='failed'", "grading_source='ai'");
        assertThat(sql(configuration, "lockTimerLease", Map.of("sessionId", 1L))).contains("for update");
        assertThat(sql(configuration, "countRecentEvidence", Map.of(
            "userId", 1L, "goalId", 2L, "evidenceGroupKey", "Q:1")))
            .contains("answered_at>=now()-interval '7 days'");
        assertThat(sql(configuration, "requeueJob", Map.of("id", 1L)))
            .contains("attempt_count=greatest(attempt_count-1,0)", "status='queued'");
    }

    @Test
    void migrationAndLocalVerificationRegisterFormalExamAlignment() throws Exception {
        String migration = Files.readString(find("script/sql/postgres/certmuse/20260819_add-formal-exam-alignment.sql"));
        String manifest = Files.readString(find("script/sql/postgres/certmuse/local-migration-manifest.txt"));
        String verification = Files.readString(find("script/sql/postgres/certmuse/verify_local.sql"));

        assertThat(migration).contains("grading_source", "grading_revision_no", "cm_formal_exam_timer_lease",
            "timing_mode IN ('standard', 'pausable')", "VALIDATE CONSTRAINT");
        assertThat(manifest).contains("20260819_add-formal-exam-alignment.sql");
        assertThat(manifest).contains("20260820_align-formal-exam-session-uniqueness.sql");
        assertThat(manifest).contains("20260820_allow-async-job-retry.sql");
        assertThat(Files.readString(find("script/sql/postgres/certmuse/20260820_align-formal-exam-session-uniqueness.sql")))
            .contains("ON cm_learning_session(user_id, goal_id)", "session_type = 'past_paper_exam'")
            .doesNotContain("ON cm_learning_session(user_id, goal_id, collection_revision_id)");
        assertThat(Files.readString(find("script/sql/postgres/certmuse/20260820_allow-async-job-retry.sql")))
            .contains("trg_cm_async_job_status", "failed>queued");
        assertThat(verification).contains("cm_formal_exam_timer_lease", "grading_revision_no", "failed>queued");
    }

    private static Configuration mapperConfiguration() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/certmuse/FormalExamMapper.xml";
        try (InputStream stream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private static String sql(Configuration configuration, String statement, Map<String, ?> parameters) {
        return configuration.getMappedStatement(NAMESPACE + statement).getBoundSql(parameters).getSql()
            .replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private static Path find(String relative) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.exists(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("file not found: " + relative);
    }
}
