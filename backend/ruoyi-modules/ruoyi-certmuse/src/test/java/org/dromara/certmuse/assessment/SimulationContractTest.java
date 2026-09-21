package org.dromara.certmuse.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.dromara.certmuse.assessment.controller.SimulationController;
import org.dromara.certmuse.assessment.domain.vo.SimulationDetailVo;
import org.dromara.certmuse.assessment.domain.vo.SimulationListItemVo;
import org.dromara.certmuse.assessment.domain.vo.SimulationPreviewQuestionVo;
import org.dromara.certmuse.assessment.domain.vo.SimulationPreviewVo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class SimulationContractTest {
    @Test
    void mapperUsesCurrentPublishedRevisionsAndActualItemAggregates() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/certmuse/SimulationMapper.xml";
        try (InputStream stream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        String namespace = "org.dromara.certmuse.assessment.mapper.SimulationMapper.";
        assertThat(configuration.hasStatement(namespace + "selectCurrentGoal")).isTrue();
        assertThat(configuration.hasStatement(namespace + "selectSetupOptions")).isTrue();
        assertThat(configuration.hasStatement(namespace + "selectPublishedSimulations")).isTrue();
        assertThat(configuration.hasStatement(namespace + "selectPublishedSimulation")).isTrue();
        assertThat(configuration.hasStatement(namespace + "selectPublishedSimulationPreviewQuestions")).isTrue();

        String listSql = normalize(configuration.getMappedStatement(namespace + "selectPublishedSimulations")
            .getBoundSql(Map.of("userId", 42L, "certificationId", 9L, "syllabusVersionId", 21L, "keyword", "模拟", "limit", 10,
                "offset", 0L)).getSql());
        assertThat(listSql).contains("from cm_collection_current current_revision",
            "r.status='published'", "r.collection_type='simulation'", "c.status='0'",
            "count(item.id)::int actual_question_count", "sum(item.report_score)", "active_session_id",
            "g.status='active'", "s.status in('created','in_progress','submitted','settling')",
            "order by r.published_time desc,c.id desc", "limit ? offset ?");
        String previewSql = normalize(configuration.getMappedStatement(namespace + "selectPublishedSimulationPreviewQuestions")
            .getBoundSql(Map.of("collectionId", 9L)).getSql());
        assertThat(previewSql).contains("item.item_order question_order", "question_revision.question_type",
            "question_revision.stem", "question_revision.status='published'", "order by item.item_order asc,item.id asc")
            .doesNotContain("question_revision.answer", "question_revision.analysis", "cm_question_option");
    }

    @Test
    void migrationAndLocalVerificationCoverTheGatedSimulationSessionContract() throws Exception {
        String migration = Files.readString(find("script/sql/postgres/certmuse/20260818_add-simulation-session-contract.sql"));
        String manifest = Files.readString(find("script/sql/postgres/certmuse/local-migration-manifest.txt"));
        String verification = Files.readString(find("script/sql/postgres/certmuse/verify_local.sql"));

        assertThat(migration).contains("WHERE session_type = 'simulation'", "timing_mode varchar(20)",
            "duration_seconds_snapshot integer", "deadline_time timestamptz", "formal_attempt_no integer",
            "fk_cm_learning_session_collection_revision", "ck_cm_learning_session_simulation_fields",
            "VALIDATE CONSTRAINT ck_cm_learning_session_simulation_fields",
            "uk_cm_learning_session_active_simulation_goal", "uk_cm_learning_session_simulation_attempt")
            .doesNotContain("ALTER TABLE cm_learning_session DROP CONSTRAINT");
        assertThat(manifest).contains("20260818_add-simulation-session-contract.sql");
        assertThat(verification).contains("U13 simulation session columns are incomplete",
            "U13 simulation collection-revision foreign key is missing",
            "U13 validated simulation session check is missing",
            "uk_cm_learning_session_active_simulation_goal", "uk_cm_learning_session_simulation_attempt",
            "expected 78 cm tables after retired scoring-point tables are removed",
            "expected 73 id primary keys after U16 exam guidance and formal-exam persistence are added",
            "expected 53 JSONB schema guards after retired scoring-point schemas are removed");
    }

    @Test
    void publicDisplayTypesDoNotHaveQuestionOrAnswerFields() {
        assertThat(SimulationController.class.getDeclaredMethods()).isNotEmpty();
        assertThat(componentNames(SimulationListItemVo.class)).doesNotContain(
            "question", "stem", "options", "answer", "analysis", "knowledgePoints", "gradingSnapshot");
        assertThat(componentNames(SimulationDetailVo.class)).doesNotContain(
            "question", "stem", "options", "answer", "analysis", "knowledgePoints", "gradingSnapshot");
        assertThat(componentNames(SimulationPreviewVo.class)).containsExactly(
            "collectionId", "revisionId", "collectionName", "questions");
        assertThat(componentNames(SimulationPreviewQuestionVo.class)).containsExactly(
            "questionOrder", "questionType", "stem");
    }

    private static String[] componentNames(Class<?> type) {
        return java.util.Arrays.stream(type.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName)
            .toArray(String[]::new);
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private static Path find(String relative) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("file not found: " + relative);
    }
}
