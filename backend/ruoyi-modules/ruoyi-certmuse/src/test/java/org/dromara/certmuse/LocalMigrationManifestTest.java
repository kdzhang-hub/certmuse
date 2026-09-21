package org.dromara.certmuse;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class LocalMigrationManifestTest {
    @Test
    void manifestContainsDependentIncrementalMigrationsInOrder() throws Exception {
        Path backend = Path.of(System.getProperty("user.dir")).resolve("..").resolve("..").normalize();
        String localRunner = Files.readString(backend.resolve(Path.of("..", "tools", "ops", "migrate-local-database.ps1")));
        String manifest = Files.readString(backend.resolve(Path.of("script", "sql", "postgres", "certmuse", "local-migration-manifest.txt")));
        String dockerfile = Files.readString(backend.resolve(Path.of("..", "infra", "docker", "postgres.Dockerfile")));
        String initScript = Files.readString(backend.resolve(Path.of("..", "infra", "docker", "postgres", "init.sh")));

        assertThat(manifest).containsSubsequence(
            "20260804_add-qualification-version-management.sql",
            "20260806_add-qualification-name-unique-index.sql",
            "20260805_add-collection-management.sql",
            "20260805_collection-three-state-rollback.sql",
            "20260805_add-collection-draft-remove.sql"
        );
        assertThat(manifest).containsSubsequence(
            "20260812_add-initial-diagnostic.sql",
            "20260812_add-diagnostic-timing.sql",
            "20260812_defer-textbook-document-creation.sql"
        );
        assertThat(localRunner).contains("local-migration-manifest.txt")
            .contains("docker cp $path")
            .contains("certmuse-local-migration.sql")
            .contains("psql -X -v ON_ERROR_STOP=1")
            .contains("Sync-LocalRegistrationConfiguration")
            .contains("LOCAL_PUBLIC_REGISTRATION");
        assertThat(dockerfile)
            .contains("20260806_add-qualification-name-unique-index.sql")
            .contains("20260805_hide-admin-content-import-menu.sql");
        assertThat(initScript).contains("apply_local_migrations")
            .contains("register_local_migrations")
            .contains("certmuse_meta.schema_migration")
            .contains("local-migration-manifest.txt");
    }
}
