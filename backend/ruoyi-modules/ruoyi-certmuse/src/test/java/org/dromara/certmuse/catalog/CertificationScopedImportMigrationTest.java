package org.dromara.certmuse.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class CertificationScopedImportMigrationTest {

    @Test
    void migrationAddsAndBackfillsCertificationScopeWhileMakingDocumentSyllabusOptional() throws Exception {
        String migrationName = "20260811_add-certification-scoped-imports.sql";
        String repairName = "20260811_fix-certification-scoped-import-context.sql";
        Path migrations = Path.of("..", "..", "script", "sql", "postgres", "certmuse").normalize();
        String sql = Files.readString(migrations.resolve(migrationName)).toLowerCase();
        String repair = Files.readString(migrations.resolve(repairName)).toLowerCase();
        String manifest = Files.readString(migrations.resolve("local-migration-manifest.txt"));
        String verification = Files.readString(migrations.resolve("verify_local.sql")).toLowerCase();

        assertThat(sql)
            .contains("alter table cm_document add column if not exists certification_id bigint")
            .contains("set certification_id = sv.certification_id")
            .contains("where d.syllabus_version_id = sv.id and d.certification_id is null")
            .contains("alter table cm_document alter column certification_id set not null")
            .contains("alter table cm_document alter column syllabus_version_id drop not null")
            .contains("foreign key (certification_id) references cm_exam_certification(id)")
            .contains("alter table cm_import_batch add column if not exists certification_id bigint")
            .contains("select d.certification_id from cm_document d where d.id = b.document_id")
            .contains("select es.certification_id from cm_exam_subject es where es.id = b.exam_subject_id");
        assertThat(repair)
            .contains("drop constraint if exists ck_cm_import_batch_context")
            .contains("import_type = 'question' and certification_id is not null")
            .contains("import_type = 'document_chunk' and certification_id is not null and document_id is not null")
            .doesNotContain("document_id is not null and syllabus_version_id is null");
        assertThat(manifest).containsSubsequence(migrationName, repairName);
        assertThat(verification)
            .contains("insert into cm_document(id,certification_id,syllabus_version_id,title,document_type)");
    }
}
