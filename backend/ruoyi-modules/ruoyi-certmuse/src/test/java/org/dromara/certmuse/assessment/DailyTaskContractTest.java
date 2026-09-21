package org.dromara.certmuse.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.dromara.certmuse.assessment.controller.DailyTaskExceptionHandler;
import org.dromara.certmuse.assessment.controller.DailyTaskController;
import org.dromara.certmuse.assessment.domain.bo.SubmitDailyTaskItemBo;
import org.dromara.certmuse.assessment.support.AssessmentJsonSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class DailyTaskContractTest {
    @Test
    void allRoutesRequireStudentAndDailyTaskPermission() throws Exception {
        for (var method : List.of(
            DailyTaskController.class.getMethod("session", String.class),
            DailyTaskController.class.getMethod("item", String.class, int.class),
            DailyTaskController.class.getMethod("submit", String.class, int.class, String.class,
                SubmitDailyTaskItemBo.class),
            DailyTaskController.class.getMethod("complete", String.class, String.class))) {
            SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
            assertThat(permission.mode()).isEqualTo(SaMode.AND);
            assertThat(permission.value()).containsExactly("certmuse:student", "certmuse:assessment:daily-task:answer");
        }
    }

    @Test
    void mapperAndMigrationFreezeDailyTaskBoundaries() throws Exception {
        String mapper = Files.readString(find("ruoyi-modules/ruoyi-certmuse/src/main/resources/mapper/certmuse/DailyTaskMapper.xml"));
        assertThat(mapper).contains("session_type='daily_task'", "max(t.display_snapshot-&gt;&gt;'title') task_title",
            "g.status='active'", "for update",
            "status='submitted'", "status='settling'", "status='completed'", "interval '24 hours'");
        String migration = Files.readString(find("script/sql/postgres/certmuse/20260819_add-daily-task-execution.sql"));
        assertThat(migration).contains("1761400000000099019", "certmuse:assessment:daily-task:answer", "student");
    }

    @Test
    void dailyTaskIdempotencyAndValidationHaveDedicatedContracts() throws Exception {
        assertThat(AssessmentJsonSchema.DAILY_TASK_ACTION_IDEMPOTENCY.version())
            .isEqualTo("daily_task_action_idempotency/1.0");
        var invalid = DailyTaskExceptionHandler.class.getMethod("invalid", Exception.class)
            .getAnnotation(org.springframework.web.bind.annotation.ExceptionHandler.class);
        assertThat(invalid.value()).contains(
            org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.validation.BindException.class);
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
