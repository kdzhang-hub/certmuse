package org.dromara.certmuse.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class TextbookPdfMigrationTest {

    @Test
    void textbookPermissionValidationUsesItsOwnMenuIdInsteadOfTheLearningTaskQueryId() throws Exception {
        String textbookMigration = Files.readString(find(
            "script/sql/postgres/certmuse/20260817_add-textbook-original-pdf.sql"));
        String validationMigration = Files.readString(find(
            "script/sql/postgres/certmuse/20260818_validate-textbook-original-pdf-permission.sql"));
        String taskMigration = Files.readString(find(
            "script/sql/postgres/certmuse/20260817_add-learning-task-pool.sql"));

        assertThat(textbookMigration)
            .contains("1761400000000099014", "certmuse:learning:textbook:query")
            .doesNotContain("1761400000000099011");
        assertThat(validationMigration).contains("1761400000000099014", "certmuse:learning:textbook:query",
            "IS DISTINCT FROM", "textbook-original-pdf permission id conflict");
        assertThat(taskMigration).contains("1761400000000099011", "certmuse:learning:task:query");
    }

    private static Path find(String relative) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative).normalize();
            if (Files.exists(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("file not found: " + relative);
    }
}
