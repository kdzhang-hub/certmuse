package org.dromara.certmuse.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class TextbookOriginalPdfMigrationTest {

    @Test
    void appliedMigrationRemainsFrozenAndCreatesAttachmentSchemaWithItsOwnLearnerPermission() throws Exception {
        Path migrations = migrationRoot();
        String migrationName = "20260817_add-textbook-original-pdf.sql";
        String migration = Files.readString(migrations.resolve(migrationName));
        String manifest = Files.readString(migrations.resolve("local-migration-manifest.txt"));
        String learnerTasks = Files.readString(migrations.resolve("20260817_add-learning-task-pool.sql"));

        assertThat(migration).contains(
            "CREATE TABLE cm_textbook_original_pdf",
            "FOREIGN KEY (document_id) REFERENCES cm_document(id) ON DELETE CASCADE",
            "idx_cm_textbook_original_pdf_document",
            "certmuse:learning:textbook:query",
            "1761400000000099014",
            "WHERE role_key = 'student'"
        );
        assertThat(learnerTasks).contains("1761400000000099011", "1761400000000099012", "1761400000000099013");
        assertThat(manifest).containsSubsequence("20260817_add-learning-task-pool.sql", migrationName);
        assertThat(migration).doesNotContain("1761400000000099011");
        assertThat(migration).contains(
            "menu_id = 1761400000000099014",
            "perms IS DISTINCT FROM 'certmuse:learning:textbook:query'",
            "perms = 'certmuse:learning:textbook:query'",
            "menu_id <> 1761400000000099014",
            "textbook original PDF permission id conflict"
        );
    }

    private static Path migrationRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("script/sql/postgres/certmuse");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("CertMuse migration root not found");
    }
}
