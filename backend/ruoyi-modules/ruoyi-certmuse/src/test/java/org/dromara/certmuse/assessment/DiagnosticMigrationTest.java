package org.dromara.certmuse.assessment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class DiagnosticMigrationTest {
    @Test
    void migrationDefinesOwnershipIdempotencyAndPermission() throws Exception {
        String sql = Files.readString(migration("20260812_add-initial-diagnostic.sql"));
        assertThat(sql).contains("uk_cm_learning_session_active_initial_diagnosis", "uk_cm_idempotency_diagnostic_request", "certmuse:learning:diagnostic");
    }

    @Test
    void timingMigrationDefinesSingleLeasePerSession() throws Exception {
        String sql = Files.readString(migration("20260812_add-diagnostic-timing.sql"));
        assertThat(sql).contains("cm_diagnostic_timer_lease", "PRIMARY KEY (session_id)", "question_order BETWEEN 1 AND 50");
        assertThat(Files.readString(migration("local-migration-manifest.txt"))).containsSubsequence(
            "20260812_add-initial-diagnostic.sql",
            "20260812_add-diagnostic-timing.sql"
        );
    }

    @Test
    void questionCountRelaxationKeepsOnlyPositiveQuestionOrdersInDatabase() throws Exception {
        String sql = Files.readString(migration("20260817_relax-initial-diagnostic-question-count.sql"));
        assertThat(sql).contains("last_question_order >= 1", "question_order >= 1")
            .doesNotContain("BETWEEN 1 AND 50");
        assertThat(Files.readString(migration("local-migration-manifest.txt")))
            .contains("20260817_relax-initial-diagnostic-question-count.sql");
    }

    private static Path migration(String name) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("script/sql/postgres/certmuse").resolve(name);
            if (Files.exists(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("CertMuse migration not found: " + name);
    }
}
