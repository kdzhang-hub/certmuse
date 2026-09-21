package org.dromara.certmuse.learning;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.dromara.certmuse.learning.controller.LearningTaskController;
import org.dromara.certmuse.learning.domain.bo.LearningTaskQueryBo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class LearningTaskContractTest {
    @Test
    void controllerUsesAllThreeAndPermissions() throws Exception {
        SaCheckPermission query = LearningTaskController.class.getMethod("page", LearningTaskQueryBo.class)
            .getAnnotation(SaCheckPermission.class);
        SaCheckPermission launch = LearningTaskController.class.getMethod("launch", String.class, String.class)
            .getAnnotation(SaCheckPermission.class);
        SaCheckPermission supplement = LearningTaskController.class.getMethod("supplement", String.class)
            .getAnnotation(SaCheckPermission.class);
        SaCheckPermission content = LearningTaskController.class.getMethod("learningContent", String.class)
            .getAnnotation(SaCheckPermission.class);
        SaCheckPermission startPractice = LearningTaskController.class.getMethod(
            "startPractice", String.class, String.class).getAnnotation(SaCheckPermission.class);

        assertThat(query.mode()).isEqualTo(SaMode.AND);
        assertThat(query.value()).containsExactly("certmuse:student", "certmuse:learning:task:query");
        assertThat(launch.mode()).isEqualTo(SaMode.AND);
        assertThat(launch.value()).containsExactly("certmuse:student", "certmuse:learning:task:launch");
        assertThat(supplement.mode()).isEqualTo(SaMode.AND);
        assertThat(supplement.value()).containsExactly("certmuse:student", "certmuse:learning:task:supplement");
        assertThat(content.mode()).isEqualTo(SaMode.AND);
        assertThat(content.value()).containsExactly("certmuse:student", "certmuse:assessment:daily-task:answer");
        assertThat(startPractice.mode()).isEqualTo(SaMode.AND);
        assertThat(startPractice.value()).containsExactly("certmuse:student", "certmuse:assessment:daily-task:answer");
    }

    @Test
    void migrationFreezesQuestionsAndEnforcesBothSessionUniquenessRules() throws Exception {
        String migration = Files.readString(find("script/sql/postgres/certmuse/20260817_add-learning-task-pool.sql"));
        assertThat(migration).contains("IF EXISTS (SELECT 1 FROM cm_learning_task)",
            "DROP COLUMN task_date", "DROP COLUMN status", "CREATE TABLE cm_task_question",
            "uk_cm_learning_session_daily_task_source", "uk_cm_learning_session_active_daily_task_goal",
            "status IN ('created','in_progress','submitted','settling')",
            "frozen_question_count NOT BETWEEN 1 AND 8",
            "expected_question_count<>frozen_question_count",
            "1761400000000099011", "1761400000000099012", "1761400000000099013");
        assertThat(migration).doesNotContain("exactly eight frozen questions");
        assertThat(migration).doesNotContain("source_task_item_id");
    }

    @Test
    void mapperContainsOwnershipLocksAndCorrectnessIndependentSubmissionCount() throws Exception {
        String mapper = Files.readString(find(
            "ruoyi-modules/ruoyi-certmuse/src/main/resources/mapper/certmuse/LearningTaskMapper.xml"));
        assertThat(mapper).contains("t.user_id=#{userId} and t.goal_id=#{goalId}",
            "for update", "qa.status='submitted'", "questionCount", "limit #{limit}",
            "TASK_RECOMMENDATION_V1", "on conflict do nothing", ")) &gt;= 1",
            "jsonb_exists(r.answer, 'schema_version')");
        assertThat(mapper).contains("#{learningContentSchemaVersion}", "cm_document_image",
            "cm_question_image", "storagePath", "completePracticeItem");
        assertThat(mapper).doesNotContain(")) &gt;= 8");
        assertThat(mapper).doesNotContain("is_correct", "score_rate=1", "task_date", "r.answer ? 'schema_version'");
    }

    @Test
    void onboardingTaskCountUsesCompletedRequiredItemsInsteadOfLegacyTaskStatus() throws Exception {
        String mapper = Files.readString(find(
            "ruoyi-modules/ruoyi-certmuse/src/main/resources/mapper/certmuse/OnboardingMapper.xml"));

        assertThat(mapper).contains("cm_task_attempt a", "a.attempt_no = 1", "a.status = 'completed'");
        assertThat(mapper).doesNotContain("status not in ('cancelled', 'replaced')");
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
