package org.dromara.certmuse.assessment;

import org.dromara.certmuse.assessment.domain.DiagnosticTimerLeaseRow;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class DiagnosticTimerLeaseMappingTest {

    @Test
    void mapsPostgresTimestamptzDirectlyToOffsetDateTimeWithoutTextCast() throws Exception {
        Path mapper = mapperXml();
        String xml = Files.readString(mapper);
        Field field = DiagnosticTimerLeaseRow.class.getDeclaredField("lastHeartbeatAt");

        assertThat(field.getType()).isEqualTo(OffsetDateTime.class);
        assertThat(xml).contains("select session_id,question_order,attempt_id,lease_id,last_heartbeat_at");
        assertThat(xml).doesNotContain("cast(last_heartbeat_at as text)");
    }

    @Test
    void selectsCurrentDiagnosticRevisionByCollectionTypeInsteadOfPurposeCode() throws Exception {
        String xml = Files.readString(mapperXml());

        assertThat(xml).contains("where c.collection_type='FIRST_DIAGNOSTIC' and r.status='published' and r.collection_type='FIRST_DIAGNOSTIC'");
        assertThat(xml).doesNotContain("cur.purpose_code='FIRST_DIAGNOSTIC'");
    }

    @Test
    void diagnosticPipelineDoesNotConsumeRetriesDuringNormalStageTransitions() throws Exception {
        String xml = Files.readString(mapperXml());

        assertThat(xml).contains("and attempt_count&lt;max_attempts order by id for update skip locked limit 1");
        assertThat(xml).contains("status='queued',attempt_count=greatest(attempt_count-1,0),payload=jsonb_set");
    }

    private static Path mapperXml() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("ruoyi-modules/ruoyi-certmuse/src/main/resources/mapper/certmuse/DiagnosticMapper.xml");
            if (Files.exists(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("Diagnostic mapper XML not found");
    }
}
