package org.dromara.certmuse.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class QualificationCascadeMigrationTest {
    @Test
    void backfillsInitialVersionsAndFinalizesCascadesAfterAllDependentTables() throws Exception {
        String backfillName = "20260807_backfill-initial-syllabus-versions.sql";
        String cascadeName = "20260807_finalize-qualification-cascade-delete.sql";
        String certificationScopeCascadeName = "20260811_finalize-certification-scope-cascades.sql";
        String revisionCascadeGuardName = "20260811_allow-qualification-cascade-question-revision-delete.sql";
        String appendOnlyCascadeGuardName = "20260811_allow-qualification-cascade-append-only-delete.sql";
        String syllabusCascadeName = "20260812_allow-syllabus-version-cascade-delete.sql";
        Path migrationRoot = Path.of("..", "..", "script", "sql", "postgres", "certmuse").normalize();
        String backfill = Files.readString(migrationRoot.resolve(backfillName)).toLowerCase();
        String cascade = Files.readString(migrationRoot.resolve(cascadeName)).toLowerCase();
        String certificationScopeCascade = Files.readString(migrationRoot.resolve(certificationScopeCascadeName)).toLowerCase();
        String revisionCascadeGuard = Files.readString(migrationRoot.resolve(revisionCascadeGuardName)).toLowerCase();
        String appendOnlyCascadeGuard = Files.readString(migrationRoot.resolve(appendOnlyCascadeGuardName)).toLowerCase();
        String syllabusCascade = Files.readString(migrationRoot.resolve(syllabusCascadeName)).toLowerCase();
        String migrationManifest = Files.readString(migrationRoot.resolve("local-migration-manifest.txt"));
        String dockerfile = Files.readString(Path.of("..", "..", "..", "infra", "docker",
            "postgres.Dockerfile").normalize());

        assertThat(backfill).contains("not exists").contains("'第一版'").contains("lock table cm_syllabus_version");
        assertThat(cascade).contains("with recursive descendant_constraints")
            .contains("constraint_item.confdeltype <> 'c'")
            .contains("on delete cascade");
        assertThat(certificationScopeCascade)
            .contains("with recursive descendant_constraints")
            .contains("cm_exam_certification")
            .contains("constraint_item.confdeltype <> 'c'")
            .contains("on delete cascade");
        assertThat(revisionCascadeGuard)
            .contains("current_setting('certmuse.qualification_cascade_delete', true)")
            .contains("old.status <> 'draft'")
            .contains("non-draft question revision content is immutable");
        assertThat(appendOnlyCascadeGuard)
            .contains("create or replace function cm_deny_mutation()")
            .contains("tg_op='delete'")
            .contains("current_setting('certmuse.qualification_cascade_delete', true) = 'on'")
            .contains("raise exception '% is append-only'");
        assertThat(syllabusCascade)
            .contains("'public.cm_syllabus_version'::regclass")
            .contains("with recursive descendant_constraints")
            .contains("on delete cascade")
            .contains("current_setting('certmuse.qualification_cascade_delete', true)");
        assertThat(migrationManifest).containsSubsequence(
            "20260807_add-paper-import.sql",
            backfillName,
            cascadeName,
            "20260811_add-certification-scoped-imports.sql",
            certificationScopeCascadeName,
            revisionCascadeGuardName,
            appendOnlyCascadeGuardName,
            syllabusCascadeName
        );
        assertThat(dockerfile).contains(backfillName).contains(cascadeName)
            .contains("COPY backend/script/sql/postgres/certmuse/ /opt/certmuse/migrations/");
    }
}
