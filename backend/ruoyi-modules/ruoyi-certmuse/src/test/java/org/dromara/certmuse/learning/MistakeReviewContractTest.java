package org.dromara.certmuse.learning;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.dromara.certmuse.learning.controller.MistakeReviewController;
import org.dromara.certmuse.learning.domain.bo.CreateMistakeCorrectionSessionBo;
import org.dromara.certmuse.learning.domain.bo.SubmitMistakeCorrectionItemBo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Regression checks for V1's stable route, permission, schema and lock boundary. */
@Tag("dev")
class MistakeReviewContractTest {
    @Test
    void controllerUsesStudentAndMistakePermissions() throws Exception {
        SaCheckPermission query = MistakeReviewController.class.getMethod("list", String.class, String.class,
            String.class, String.class, Integer.class, Integer.class, Integer.class).getAnnotation(SaCheckPermission.class);
        SaCheckPermission create = MistakeReviewController.class.getMethod("create", String.class,
            CreateMistakeCorrectionSessionBo.class).getAnnotation(SaCheckPermission.class);
        SaCheckPermission submit = MistakeReviewController.class.getMethod("submit", long.class, int.class,
            String.class, SubmitMistakeCorrectionItemBo.class).getAnnotation(SaCheckPermission.class);

        assertThat(query.mode()).isEqualTo(SaMode.AND);
        assertThat(query.value()).containsExactly("certmuse:student", "certmuse:learning:mistake:query");
        assertThat(create.mode()).isEqualTo(SaMode.AND);
        assertThat(create.value()).containsExactly("certmuse:student", "certmuse:learning:mistake:correct");
        assertThat(submit.mode()).isEqualTo(SaMode.AND);
        assertThat(submit.value()).containsExactly("certmuse:student", "certmuse:learning:mistake:correct");
    }

    @Test
    void migrationAddsOnlyTheSpecifiedCorrectionConstraintsAndPermissions() throws Exception {
        String migration = Files.readString(find("script/sql/postgres/certmuse/20260818_add-mistake-review.sql"));
        assertThat(migration).contains("uk_cm_learning_session_active_correction",
            "fk_cm_correction_record_correction_attempt_id", "idx_cm_error_record_user_goal_status_detected",
            "certmuse:learning:mistake:query", "certmuse:learning:mistake:correct",
            "mistake-review permission id/code conflict", "ON CONFLICT(menu_id) DO UPDATE");
        assertThat(migration).doesNotContain("cm_idempotency_record(request_id)");
    }

    @Test
    void mapperUsesFrozenSnapshotsAndCurrentGoalLeafNames() throws Exception {
        String mapper = Files.readString(find("ruoyi-modules/ruoyi-certmuse/src/main/resources/mapper/certmuse/MistakeReviewMapper.xml"));
        assertThat(mapper).contains("detected_time desc,a.id desc,e.id desc", "for update",
            "status_reason", "current_knowledge_points", "kp.syllabus_version_id=goal.syllabus_version_id",
            "on conflict(error_record_id) do nothing", "request_id=#{requestId}");
        assertThat(mapper).contains("status='in_progress'</update>");
    }

    @Test
    void mapperLocksCorrectionSessionBeforeAggregatingAnswerCardProgress() throws Exception {
        String mapper = Files.readString(find("ruoyi-modules/ruoyi-certmuse/src/main/resources/mapper/certmuse/MistakeReviewMapper.xml"));

        assertThat(mapper).contains("with locked_session as", "from cm_learning_session", "for update",
            "from locked_session s", "group by s.id,s.user_id,s.goal_id,s.rule_version_id,s.status");
        assertThat(mapper).doesNotContain("group by s.id for update of s");
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
