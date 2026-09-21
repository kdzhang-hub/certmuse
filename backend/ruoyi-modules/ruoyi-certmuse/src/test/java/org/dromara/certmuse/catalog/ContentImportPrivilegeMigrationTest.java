package org.dromara.certmuse.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class ContentImportPrivilegeMigrationTest {

    @Test
    void restoresApplicationDmlAccessForPostBaselineContentImportTables() throws Exception {
        String migrationName = "20260812_grant-content-import-table-privileges.sql";
        Path migrations = Path.of("..", "..", "script", "sql", "postgres", "certmuse").normalize();
        String sql = Files.readString(migrations.resolve(migrationName)).toLowerCase();
        String manifest = Files.readString(migrations.resolve("local-migration-manifest.txt"));

        assertThat(sql)
            .contains("grant select, insert, update, delete on table cm_knowledge_import_diff to certmuse_app")
            .contains("grant select, insert, update, delete on table cm_paper_import to certmuse_app")
            .doesNotContain("grant all");
        assertThat(manifest).contains(migrationName);
    }
}
