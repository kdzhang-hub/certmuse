package org.dromara.certmuse.question;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class CollectionManagementMigrationTest {
    @Test
    void migrationAddsSnapshotsLinearWorkflowAndPermissions() throws Exception {
        Path migration = Path.of("..", "..", "script", "sql", "postgres", "certmuse",
            "20260805_add-collection-management.sql").normalize();
        String sql = Files.readString(migration).toLowerCase();
        assertThat(sql).contains("add column if not exists collection_name")
            .contains("pending_review>draft")
            .contains("pending_review>published")
            .contains("where status in ('draft', 'pending_review')")
            .contains("certmuse:question:collection:submit-review")
            .contains("certmuse:question:collection:review");
    }

    @Test
    void threeStateMigrationKeepsPublishedHistoryAndValidatesCurrentPointer() throws Exception {
        Path migration = Path.of("..", "..", "script", "sql", "postgres", "certmuse",
            "20260805_collection-three-state-rollback.sql").normalize();
        String sql = Files.readString(migration).toLowerCase();
        assertThat(sql).contains("status in ('draft','pending_review','published')")
            .contains("drop index if exists uk_cm_collection_revision_published")
            .contains("where status in ('superseded','offline')")
            .contains("cm_validate_collection_current")
            .doesNotContain("published>superseded");
    }

    @Test
    void reviewAlignmentMigrationAllowsManyDraftsAndOnePendingReview() throws Exception {
        Path migration = Path.of("..", "..", "script", "sql", "postgres", "certmuse",
            "20260806_align-collection-review-contract.sql").normalize();
        String sql = Files.readString(migration).toLowerCase();
        assertThat(sql).contains("certmuse:question:collection:check")
            .contains("submit_collection_review")
            .contains("approve_collection")
            .contains("reject_collection")
            .contains("offline_collection_revision")
            .contains("uk_cm_collection_revision_pending_review")
            .contains("where status='pending_review'")
            .contains("published>draft")
            .contains("on cm_idempotency_record(request_id)");
    }

    @Test
    void jointReviewMigrationAddsRealRejectedStateBackfillAndReverseIndex() throws Exception {
        Path migration = Path.of("..", "..", "script", "sql", "postgres", "certmuse",
            "20260807_collection-question-joint-review.sql").normalize();
        String sql = Files.readString(migration).toLowerCase();
        assertThat(sql).contains("status = 'rejected'")
            .contains("nullif(btrim(review_opinion), '') is not null")
            .contains("status in ('draft', 'pending_review', 'published', 'rejected')")
            .contains("rejected>pending_review")
            .contains("pending_review>rejected")
            .contains("idx_cm_collection_item_question_revision_collection");
        assertThat(sql.indexOf("drop constraint if exists ck_cm_collection_revision_status"))
            .isLessThan(sql.indexOf("set status = 'rejected'"));
    }
}
