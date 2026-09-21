package org.dromara.certmuse.learning;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class LearningHistoryPermissionMigrationTest {
    @Test
    void migrationIsIdempotentAndGrantsStudentRole() throws Exception {
        Path root = migrationRoot();
        String name = "20260819_add-learning-history-permission.sql";
        String migration = Files.readString(root.resolve(name));
        String manifest = Files.readString(root.resolve("local-migration-manifest.txt"));

        assertThat(migration).contains("certmuse:learning:history:query", "WHERE NOT EXISTS", "ON CONFLICT DO NOTHING",
            "role_key='student'");
        assertThat(manifest).contains(name);
    }

    @Test
    void practiceHistoryIndexIsPartialAndRegistered() throws Exception {
        Path root = migrationRoot();
        String name = "20260821_add-learning-practice-history-index.sql";
        String migration = Files.readString(root.resolve(name));

        assertThat(migration).contains("user_id, goal_id, settled_time DESC, id DESC",
            "session_type IN ('self_practice', 'past_paper_practice')", "status = 'completed'",
            "settled_time IS NOT NULL");
        assertThat(Files.readString(root.resolve("local-migration-manifest.txt"))).contains(name);
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
