package org.dromara.certmuse.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.dromara.certmuse.assessment.controller.KnowledgePracticeController;
import org.dromara.certmuse.assessment.support.AssessmentJsonSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class KnowledgePracticeContractTest {
    @Test
    void controllerUsesAndPermissions() throws Exception {
        SaCheckPermission setup = KnowledgePracticeController.class.getMethod("setup")
            .getAnnotation(SaCheckPermission.class);
        SaCheckPermission start = KnowledgePracticeController.class.getMethod("start", String.class,
            org.dromara.certmuse.assessment.domain.bo.StartKnowledgePracticeBo.class)
            .getAnnotation(SaCheckPermission.class);
        assertThat(setup.mode()).isEqualTo(SaMode.AND);
        assertThat(setup.value()).containsExactly("certmuse:student", "certmuse:assessment:knowledge-practice:query");
        assertThat(start.mode()).isEqualTo(SaMode.AND);
    }

    @Test
    void migrationAndMapperContainAtomicConcurrencyGuards() throws Exception {
        String migration = Files.readString(find("script/sql/postgres/certmuse/20260814_add-knowledge-practice.sql"));
        String repairMigration = Files.readString(find(
            "script/sql/postgres/certmuse/20260814_00_backfill-knowledge-point-importance.sql"));
        String mapper = Files.readString(find("ruoyi-modules/ruoyi-certmuse/src/main/resources/mapper/certmuse/KnowledgePracticeMapper.xml"));
        String manifest = Files.readString(find("script/sql/postgres/certmuse/local-migration-manifest.txt"));
        assertThat(migration).contains("uk_cm_learning_session_active_self_practice", "ALTER COLUMN importance SET NOT NULL",
            "certmuse:assessment:knowledge-practice:query", "certmuse:assessment:knowledge-practice:start");
        assertThat(repairMigration).contains("d03f81fc8b64be5c300773782977e5390c95f467388fc475aac4091e5e16a6d3",
            "708c3e7f22675c1bad65ecb2d63240fa", "point.importance IS NULL",
            "existing non-null data differs from the approved fixture");
        assertThat(mapper).contains("on conflict do nothing", "with recursive scope", "START_KNOWLEDGE_PRACTICE");
        assertThat(manifest).containsSubsequence("20260814_00_backfill-knowledge-point-importance.sql",
            "20260814_add-knowledge-practice.sql");
    }

    @Test
    void mapperUsesBottomTwoLevelCroppingAndChoiceOnlyEligibility() throws Exception {
        String mapper = Files.readString(find(
            "ruoyi-modules/ruoyi-certmuse/src/main/resources/mapper/certmuse/KnowledgePracticeMapper.xml"));

        assertThat(mapper).contains("r.question_type = 'CHOICE'", "max(tree_depth) max_depth");
        assertThat(mapper).contains("kp.tree_depth &lt;= bounds.max_depth - 2");
        assertThat(mapper).contains("v.tree_depth in (2,3)");
        assertThat(mapper).doesNotContain("not exists(select 1 from visible child where child.parent_id=v.id)");
        assertThat(mapper).contains("jsonb_exists(r.answer, 'schema_version')", "jsonb_exists(r.answer, 'value')");
        assertThat(mapper).contains("coalesce(nullif(btrim(base.evidence_group_key), ''), concat('Q:', q.question_id))");
        assertThat(mapper).contains("coalesce(r.difficulty, 'medium') difficulty");
        assertThat(mapper).contains("p.id,#{settlementId},'answer'");
        assertThat(mapper).doesNotContain("r.answer ? 'schema_version'", "r.answer ? 'selection_mode'");
        assertThat(mapper).doesNotContain("r.knowledge_mapping_status = 'reviewed'",
            "q.evidence_group_key is not null", "length(q.evidence_group_key) &lt;= 100",
            "r.difficulty in ('easy','medium','hard')", "'practice_settlement',");
        assertThat(mapper).doesNotContain("leaf_children", "branch_children");
    }

    @Test
    void idempotencyResponseUsesTheRequiredSchemaEnvelope() throws Exception {
        String service = Files.readString(find(
            "ruoyi-modules/ruoyi-certmuse/src/main/java/org/dromara/certmuse/assessment/service/impl/KnowledgePracticeServiceImpl.java"));

        assertThat(AssessmentJsonSchema.KNOWLEDGE_PRACTICE_IDEMPOTENCY.version())
            .isEqualTo("knowledge_practice_idempotency/1.0");
        assertThat(AssessmentJsonSchema.KNOWLEDGE_PRACTICE_ACTION_IDEMPOTENCY.version())
            .isEqualTo("knowledge_practice_action_idempotency/1.0");
        assertThat(service).contains("root.has(\"response\")");
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
