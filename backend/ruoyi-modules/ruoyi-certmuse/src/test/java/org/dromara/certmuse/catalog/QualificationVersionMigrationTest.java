package org.dromara.certmuse.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class QualificationVersionMigrationTest {
    @Test
    void migrationGuardsDataThenAddsAtomicConstraintIndexesAndAdminPermissions() throws Exception {
        Path backend = Path.of(System.getProperty("user.dir")).resolve("..").resolve("..").normalize();
        String sql = Files.readString(backend.resolve(Path.of("script", "sql", "postgres", "certmuse", "20260804_add-qualification-version-management.sql")));
        assertThat(sql).contains("BEGIN;").contains("COMMIT;")
            .contains("qualification_level IN ('HIGH', 'MIDDLE', 'LOW')")
            .contains("lower(certification_code)").contains("lower(version_name)")
            .contains("role_key = 'superadmin'")
            .contains("certmuse:catalog:subject:add").contains("certmuse:catalog:subject:edit").contains("certmuse:catalog:subject:remove");
    }

    @Test
    void frozenQualificationConstraintIsPreservedAndIncrementalMigrationIsIdempotent() throws Exception {
        Path backend = Path.of(System.getProperty("user.dir")).resolve("..").resolve("..").normalize();
        Path migrations = backend.resolve(Path.of("script", "sql", "postgres", "certmuse"));
        String appliedMigration = Files.readString(migrations.resolve("20260804_add-qualification-version-management.sql"));
        String incrementalMigration = Files.readString(migrations.resolve("20260806_add-qualification-name-unique-index.sql"));

        assertThat(appliedMigration)
            .contains("lower(certification_name)")
            .contains("uk_cm_exam_certification_name_ci");
        assertThat(incrementalMigration).contains("BEGIN;").contains("COMMIT;")
            .contains("lower(certification_name)")
            .contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_cm_exam_certification_name_ci");
    }
}
