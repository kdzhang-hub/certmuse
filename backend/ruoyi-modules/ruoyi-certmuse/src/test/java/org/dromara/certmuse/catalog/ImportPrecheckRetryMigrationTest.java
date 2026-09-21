package org.dromara.certmuse.catalog;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ImportPrecheckRetryMigrationTest {

    @Test
    void permitsOnlyThePrecheckRetryTransitionRequiredByTheBatchReset() throws Exception {
        String migrationName = "20260807_allow-import-precheck-retry.sql";
        String sql = Files.readString(Path.of("..", "..", "script", "sql", "postgres", "certmuse", migrationName)
            .normalize()).toLowerCase();
        String manifest = Files.readString(Path.of("..", "..", "script", "sql", "postgres", "certmuse",
            "local-migration-manifest.txt").normalize());
        String dockerfile = Files.readString(Path.of("..", "..", "..", "infra", "docker",
            "postgres.Dockerfile").normalize());

        assertThat(sql)
            .contains("drop trigger if exists trg_cm_import_batch_status")
            .contains("validating>parsing")
            .contains("validating>waiting_confirm")
            .contains("waiting_confirm>importing");
        assertThat(manifest).contains(migrationName);
        assertThat(dockerfile).contains(migrationName);
    }
}
