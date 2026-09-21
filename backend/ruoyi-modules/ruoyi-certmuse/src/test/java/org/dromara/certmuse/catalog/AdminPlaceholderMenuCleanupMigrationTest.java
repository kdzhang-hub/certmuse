package org.dromara.certmuse.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class AdminPlaceholderMenuCleanupMigrationTest {

    @Test
    void removesOnlyDuplicateContentRouteMenusAndPreservesTheirParents() throws Exception {
        Path migrations = migrationRoot();
        String migrationName = "20260824_remove-duplicate-content-placeholder-menus.sql";
        String migration = Files.readString(migrations.resolve(migrationName));
        String manifest = Files.readString(migrations.resolve("local-migration-manifest.txt"));

        assertThat(migration).contains(
            "DELETE FROM sys_role_menu",
            "1761400000000090403",
            "1761400000000090404",
            "1761400000000090410",
            "1761400000000090411",
            "1761400000000090420",
            "certmuse/catalog/resource/edit",
            "certmuse/catalog/resource/preview",
            "certmuse/question/question/edit",
            "certmuse/question/question/preview",
            "certmuse/question/review/detail",
            "1761400000000090130",
            "1761400000000090140",
            "1761400000000090210"
        );
        assertThat(manifest).containsSubsequence(
            "20260821_reconcile-production-migration-gaps.sql",
            migrationName
        );
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
