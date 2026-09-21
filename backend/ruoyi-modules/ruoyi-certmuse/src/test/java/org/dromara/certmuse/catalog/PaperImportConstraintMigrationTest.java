package org.dromara.certmuse.catalog;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class PaperImportConstraintMigrationTest {

    @Test
    void registersPaperBatchConstraintRepairAcrossAllLocalEntrypoints() throws Exception {
        String migrationName = "20260807_fix-paper-import-batch-constraints.sql";
        Path migrations = Path.of("..", "..", "script", "sql", "postgres", "certmuse").normalize();
        String sql = Files.readString(migrations.resolve(migrationName)).toLowerCase();
        String manifest = Files.readString(migrations.resolve("local-migration-manifest.txt"));
        String localRunner = Files.readString(Path.of("..", "..", "..", "tools", "ops", "migrate-local-database.ps1").normalize());
        String dockerfile = Files.readString(Path.of("..", "..", "..", "infra", "docker", "postgres.Dockerfile").normalize());
        String initScript = Files.readString(Path.of("..", "..", "..", "infra", "docker", "postgres", "init.sh").normalize());

        assertThat(sql)
            .contains("drop constraint if exists ck_cm_import_batch_import_type")
            .contains("drop constraint if exists ck_cm_import_batch_context")
            .contains("'paper'");
        assertThat(manifest).containsSubsequence("20260807_add-paper-import.sql", migrationName, "20260807_rename-syllabus-management-menu.sql");
        assertThat(localRunner).contains("local-migration-manifest.txt");
        assertThat(dockerfile).contains(migrationName).contains("/opt/certmuse/migrations/");
        assertThat(initScript).contains("local-migration-manifest.txt")
            .contains("schema_migration")
            .contains("apply_local_migrations");
    }
}
