package org.dromara.certmuse.learning;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class LearnerPermissionRepairMigrationTest {

    @Test
    void repairMigrationPreservesHistoricalMigrationsAndRepairsAppliedState() throws Exception {
        Path migrations = migrationRoot();
        String repairName = "20260818_fix-learner-permission-and-correction-constraint.sql";
        String repair = Files.readString(migrations.resolve(repairName));
        String goalMigration = Files.readString(migrations.resolve("20260818_add-learning-goal-switch.sql"));
        String aiMigration = Files.readString(migrations.resolve("20260818_add-practice-ai-chat.sql"));
        String mistakeMigration = Files.readString(migrations.resolve("20260818_add-mistake-review.sql"));
        String manifest = Files.readString(migrations.resolve("local-migration-manifest.txt"));

        assertThat(goalMigration).contains("DECLARE permission_id bigint := 1761400000000099015;");
        assertThat(aiMigration).contains("DECLARE permission_id bigint := 1761400000000099018;");
        assertThat(mistakeMigration).contains("ADD CONSTRAINT fk_cm_correction_record_correction_attempt_id");
        assertThat(repair).contains(
            "certmuse:internal:learner-permission-swap:",
            "WHERE menu_id = 1761400000000099015",
            "WHERE menu_id = 1761400000000099018",
            "fk_cm_correction_record_correction_attempt_id",
            "IF NOT EXISTS"
        );
        assertThat(manifest).containsSubsequence("20260818_add-mistake-review.sql", repairName);
    }

    private static Path migrationRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("script/sql/postgres/certmuse");
            if (Files.isDirectory(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("CertMuse migration root not found");
    }
}
