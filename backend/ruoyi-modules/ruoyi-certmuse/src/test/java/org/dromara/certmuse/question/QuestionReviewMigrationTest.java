package org.dromara.certmuse.question;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class QuestionReviewMigrationTest {
    @Test
    void questionStatusesAreReducedToTheFourStateModelEverywhere() throws Exception {
        String migrationName = "20260806_reduce-question-statuses.sql";
        Path migration = Path.of("..", "..", "script", "sql", "postgres", "certmuse", migrationName).normalize();
        String sql = Files.readString(migration).toLowerCase();
        String localRunner = Files.readString(Path.of("..", "..", "..", "tools", "ops",
            "migrate-local-database.ps1").normalize());
        String dockerfile = Files.readString(Path.of("..", "..", "..", "infra", "docker",
            "postgres.Dockerfile").normalize());
        String initScript = Files.readString(Path.of("..", "..", "..", "infra", "docker", "postgres",
            "init.sh").normalize());

        assertThat(sql).contains("status in ('draft', 'pending_review', 'rejected', 'published')")
            .contains("pending_review>published")
            .contains("pending_review>rejected")
            .contains("rejected>draft")
            .contains("published>draft")
            .doesNotContain("pending_review>approved", "published>offline", "published>superseded");
        assertThat(migrationManifest()).contains(migrationName);
        assertThat(dockerfile).contains(migrationName);
        assertThat(initScript).contains("local-migration-manifest.txt").contains("apply_local_migrations");
    }

    @Test
    void migrationAddsDirectReviewTransitionsAndScopedRequestIdUniqueness() throws Exception {
        Path migration = Path.of("..", "..", "script", "sql", "postgres", "certmuse",
            "20260806_add-question-review-interfaces.sql").normalize();
        String sql = Files.readString(migration).toLowerCase();

        assertThat(sql).contains("pending_review>published")
            .contains("pending_review>draft")
            .contains("rejected>draft")
            .contains("approved>published")
            .contains("uk_cm_idempotency_question_review_request_id")
            .contains("approve_question_revision")
            .contains("reject_question_revision");
    }

    @Test
    void localDatabaseEntrypointsIncludeTheReviewMigration() throws Exception {
        String migrationName = "20260806_add-question-review-interfaces.sql";
        String localRunner = Files.readString(Path.of("..", "..", "..", "tools", "ops",
            "migrate-local-database.ps1").normalize());
        String dockerfile = Files.readString(Path.of("..", "..", "..", "infra", "docker",
            "postgres.Dockerfile").normalize());

        assertThat(migrationManifest()).contains(migrationName);
        assertThat(localRunner).contains("docker cp $path").contains("certmuse-local-migration.sql");
        assertThat(dockerfile).contains(migrationName);
    }

    @Test
    void rejectedQuestionResubmissionIsRestoredAfterTheOfflineTransitionRepair() throws Exception {
        String migrationName = "20260807_restore-rejected-question-resubmission.sql";
        Path migration = Path.of("..", "..", "script", "sql", "postgres", "certmuse", migrationName).normalize();
        String sql = Files.readString(migration).toLowerCase();
        String dockerfile = Files.readString(Path.of("..", "..", "..", "infra", "docker",
            "postgres.Dockerfile").normalize());
        String initScript = Files.readString(Path.of("..", "..", "..", "infra", "docker", "postgres",
            "init.sh").normalize());
        String verification = Files.readString(Path.of("..", "..", "script", "sql", "postgres", "certmuse",
            "verify_local.sql").normalize());

        assertThat(sql).contains("rejected>pending_review");
        assertThat(migrationManifest()).contains(migrationName);
        assertThat(dockerfile).contains(migrationName);
        assertThat(initScript).contains("local-migration-manifest.txt").contains("apply_local_migrations");
        assertThat(verification).contains("rejected>pending_review");
    }

    @Test
    void collectionPublishCheckRemovalIsRegisteredEverywhere() throws Exception {
        String migrationName = "20260806_remove-collection-publish-check.sql";
        Path migration = Path.of("..", "..", "script", "sql", "postgres", "certmuse", migrationName).normalize();
        String sql = Files.readString(migration).toLowerCase();
        String localRunner = Files.readString(Path.of("..", "..", "..", "tools", "ops",
            "migrate-local-database.ps1").normalize());
        String dockerfile = Files.readString(Path.of("..", "..", "..", "infra", "docker",
            "postgres.Dockerfile").normalize());

        assertThat(sql).contains("certmuse:question:collection:check").contains("status = '1'");
        assertThat(migrationManifest()).contains(migrationName);
        assertThat(dockerfile).contains(migrationName);
    }

    @Test
    void submitReviewPermissionMigrationIsRegisteredEverywhere() throws Exception {
        String migrationName = "20260806_add-question-submit-review-permission.sql";
        Path migration = Path.of("..", "..", "script", "sql", "postgres", "certmuse", migrationName).normalize();
        String sql = Files.readString(migration).toLowerCase();
        String localRunner = Files.readString(Path.of("..", "..", "..", "tools", "ops",
            "migrate-local-database.ps1").normalize());
        String dockerfile = Files.readString(Path.of("..", "..", "..", "infra", "docker",
            "postgres.Dockerfile").normalize());
        String verification = Files.readString(Path.of("..", "..", "script", "sql", "postgres", "certmuse",
            "verify_local.sql").normalize());

        assertThat(sql).contains("certmuse:question:submit-review").contains("1761400000000090413");
        assertThat(migrationManifest()).contains(migrationName);
        assertThat(dockerfile).contains(migrationName);
        assertThat(verification).contains("certmuse:question:submit-review");
    }

    @Test
    void semanticHashMigrationIsRegisteredAndPreservesImmutableHistory() throws Exception {
        String migrationName = "20260806_add-question-semantic-hash.sql";
        Path migration = Path.of("..", "..", "script", "sql", "postgres", "certmuse", migrationName).normalize();
        String sql = Files.readString(migration).toLowerCase();
        String localRunner = Files.readString(Path.of("..", "..", "..", "tools", "ops",
            "migrate-local-database.ps1").normalize());
        String dockerfile = Files.readString(Path.of("..", "..", "..", "infra", "docker",
            "postgres.Dockerfile").normalize());
        String initScript = Files.readString(Path.of("..", "..", "..", "infra", "docker", "postgres",
            "init.sh").normalize());
        String verification = Files.readString(Path.of("..", "..", "script", "sql", "postgres", "certmuse",
            "verify_local.sql").normalize());

        assertThat(sql).contains("add column if not exists semantic_hash")
            .contains("disable trigger trg_cm_question_revision_immutable")
            .contains("enable trigger trg_cm_question_revision_immutable")
            .contains("alter column semantic_hash set not null");
        assertThat(migrationManifest()).contains(migrationName);
        assertThat(dockerfile).contains(migrationName);
        assertThat(initScript).contains("local-migration-manifest.txt").contains("apply_local_migrations");
        assertThat(verification).contains("question revision semantic hash column is missing");
    }

    @Test
    void reviewMetadataMigrationIsRegisteredEverywhere() throws Exception {
        String migrationName = "20260806_add-question-review-metadata.sql";
        Path migration = Path.of("..", "..", "script", "sql", "postgres", "certmuse", migrationName).normalize();
        String sql = Files.readString(migration).toLowerCase();
        String localRunner = Files.readString(Path.of("..", "..", "..", "tools", "ops",
            "migrate-local-database.ps1").normalize());
        String dockerfile = Files.readString(Path.of("..", "..", "..", "infra", "docker",
            "postgres.Dockerfile").normalize());
        String initScript = Files.readString(Path.of("..", "..", "..", "infra", "docker", "postgres",
            "init.sh").normalize());
        String verification = Files.readString(Path.of("..", "..", "script", "sql", "postgres", "certmuse",
            "verify_local.sql").normalize());

        assertThat(sql).contains("add column if not exists reviewed_by")
            .contains("add column if not exists reviewed_time")
            .contains("add column if not exists review_opinion")
            .contains("add column if not exists published_by")
            .contains("add column if not exists published_time");
        assertThat(migrationManifest()).contains(migrationName);
        assertThat(dockerfile).contains(migrationName);
        assertThat(initScript).contains("local-migration-manifest.txt").contains("apply_local_migrations");
        assertThat(verification).contains("question revision review metadata columns are missing");
    }
    private static String migrationManifest() throws Exception {
        return Files.readString(Path.of("..", "..", "script", "sql", "postgres", "certmuse",
            "local-migration-manifest.txt").normalize());
    }
}
