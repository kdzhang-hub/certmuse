package org.dromara.certmuse.assessment.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Guards the durable lease-recovery SQL state transition. */
@Tag("dev")
class AiGradingMapperContractTest {
    @Test
    void expiredProcessingTasksAreRetriedOrFailedByTheirExistingRetryCount() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/certmuse/AiGradingMapper.xml";
        try (InputStream stream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String sql = configuration.getMappedStatement(
                "org.dromara.certmuse.assessment.mapper.AiGradingMapper.reclaimExpiredTasks")
            .getBoundSql(Map.of("leaseSeconds", 120L)).getSql().replaceAll("\\s+", " ").trim().toLowerCase();

        assertThat(sql).contains("status=case when retry_count < 2 then 'pending' else 'failed' end",
            "error_code='ai_grading_lease_expired'", "coalesce(claimed_time, create_time)", "interval '1 second'");
    }
}
