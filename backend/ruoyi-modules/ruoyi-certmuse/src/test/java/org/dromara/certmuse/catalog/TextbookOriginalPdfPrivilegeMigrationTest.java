package org.dromara.certmuse.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class TextbookOriginalPdfPrivilegeMigrationTest {

    @Test
    void grantsOnlyRequiredApplicationDmlPrivilegesAndRegistersVerification() throws Exception {
        Path migrations = migrationRoot();
        String migrationName = "20260819_grant-textbook-original-pdf-privileges.sql";
        String migration = Files.readString(migrations.resolve(migrationName)).toLowerCase(Locale.ROOT);
        String manifest = Files.readString(migrations.resolve("local-migration-manifest.txt"));
        String verification = Files.readString(migrations.resolve("verify_local.sql")).toLowerCase(Locale.ROOT);

        assertThat(migration)
            .contains("\\set on_error_stop on", "begin;", "commit;")
            .contains("grant select, insert, update, delete on table cm_textbook_original_pdf to certmuse_app")
            .doesNotContain("grant all", "truncate", "references", "trigger");
        assertThat(manifest).containsSubsequence(
            "20260817_add-textbook-original-pdf.sql",
            "20260818_validate-textbook-original-pdf-permission.sql",
            migrationName
        );
        assertThat(verification)
            .contains("has_table_privilege('certmuse_app', 'public.cm_textbook_original_pdf', 'select')")
            .contains("has_table_privilege('certmuse_app', 'public.cm_textbook_original_pdf', 'insert')")
            .contains("has_table_privilege('certmuse_app', 'public.cm_textbook_original_pdf', 'update')")
            .contains("has_table_privilege('certmuse_app', 'public.cm_textbook_original_pdf', 'delete')");
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
