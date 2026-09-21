package org.dromara.certmuse.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class QuestionOfflinePermissionMigrationTest {

    @Test
    void registersTheDedicatedOfflinePermissionWithTheQuestionMenu() throws Exception {
        Path migrations = migrationRoot();
        String migrationName = "20260824_add-question-offline-permission.sql";
        String migration = Files.readString(migrations.resolve(migrationName));
        String manifest = Files.readString(migrations.resolve("local-migration-manifest.txt"));

        assertThat(migration).contains(
            "1761400000000090414",
            "1761400000000090140",
            "certmuse:question:offline",
            "ON CONFLICT (menu_id)"
        );
        assertThat(manifest).containsSubsequence(
            "20260824_remove-duplicate-content-placeholder-menus.sql",
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
