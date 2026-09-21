package org.dromara.certmuse.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.dromara.certmuse.shared.web.CertMuseExceptionHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class OverallProfileScoreContractTest {

    @Test
    void permissionMigrationIsHiddenIdempotentAndGrantedToActiveStudents() throws Exception {
        String migration = Files.readString(find(
            "script/sql/postgres/certmuse/20260817_add-overall-profile-score-permission.sql"));
        String manifest = Files.readString(find(
            "script/sql/postgres/certmuse/local-migration-manifest.txt"));

        assertThat(migration)
            .contains("1761400000000099010")
            .contains("certmuse:profile:overall-score:query")
            .contains("'F', '1', '0'")
            .contains("role_key = 'student'")
            .contains("status = '0'")
            .contains("del_flag = '0'")
            .contains("ON CONFLICT DO NOTHING")
            .contains("IS DISTINCT FROM");
        assertThat(manifest).contains("20260817_add-overall-profile-score-permission.sql");
    }

    @Test
    void repositoryContractUsesSharedUnknownFailureAndNoStore() throws Exception {
        String contract = Files.readString(find(
            "docs/requirements/学习画像总分后端接口契约V1.md"));

        assertThat(contract)
            .contains("certmuse:student")
            .contains("certmuse:profile:overall-score:query")
            .contains("Cache-Control: no-store")
            .contains("`INTERNAL_SERVER_ERROR`")
            .doesNotContain("CURRENT_PROFILE_SCORE_SYSTEM_FAILURE");
    }

    @Test
    void sharedUnexpectedFailureProvidesStableSafeEnvelope() {
        var response = new CertMuseExceptionHandler()
            .handleUnexpected(new IllegalStateException("database secret"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getCode()).isEqualTo(500);
        assertThat(response.getBody().getData().errorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().getData().retryable()).isTrue();
        assertThat(response.getBody().getData().traceId()).isNotBlank();
        assertThat(response.getBody().getMsg()).doesNotContain("database secret");
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
