package org.dromara.certmuse.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class ProductionReconciliationMigrationTest {

    @Test
    void reconcilesKnownProductionGapsWithoutReplayingOrDestroyingHistory() throws Exception {
        String migrationName = "20260821_reconcile-production-migration-gaps.sql";
        String migration = Files.readString(migrationRoot().resolve(migrationName)).toLowerCase(Locale.ROOT);
        String manifest = Files.readString(migrationRoot().resolve("local-migration-manifest.txt"));

        assertThat(migration)
            .contains("\\set on_error_stop on", "begin;", "commit;")
            .contains("add column if not exists exam_year integer", "add column if not exists exam_month smallint",
                "add column if not exists paper_type_code varchar(50)",
                "add column if not exists paper_type_name varchar(100)")
            .contains("fk_cm_question_ai_rubric_revision", "fk_cm_ai_grading_task_revision",
                "fk_cm_ai_grading_task_session_question", "fk_cm_ai_grading_task_attempt")
            .contains("grant select, insert, update on table", "grant delete on table cm_diagnostic_timer_lease",
                "grant select, insert on table")
            .doesNotContain("grant all", "drop table", "truncate");
        assertThat(manifest).containsSubsequence(
            "20260820_hide-exam-registration-menu.sql",
            migrationName
        );
    }

    private static Path migrationRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("script/sql/postgres/certmuse");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("CertMuse migration root not found");
    }
}
