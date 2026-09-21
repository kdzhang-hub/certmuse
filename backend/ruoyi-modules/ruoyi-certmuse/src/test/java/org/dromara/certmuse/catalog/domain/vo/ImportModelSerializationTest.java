package org.dromara.certmuse.catalog.domain.vo;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ImportModelSerializationTest {

    private final JsonMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void preservesImportBatchResponseShape() throws Exception {
        ImportBatchVo batch = new ImportBatchVo(
            "10001",
            "knowledge_point",
            "20001",
            null,
            null,
            java.util.List.of(),
            "knowledge_point/1.0",
            "uploaded",
            false,
            OffsetDateTime.parse("2026-07-30T10:30:00+08:00")
        );

        var json = objectMapper.readTree(objectMapper.writeValueAsBytes(batch));

        assertThat(json.size()).isEqualTo(12);
        assertThat(json.path("id").stringValue()).isEqualTo("10001");
        assertThat(json.path("documentId").isNull()).isTrue();
        assertThat(json.path("importType").stringValue()).isEqualTo("knowledge_point");
        assertThat(json.path("mode").isNull()).isTrue();
        assertThat(json.path("syllabusVersionId").stringValue()).isEqualTo("20001");
        assertThat(json.path("examSubjectId").isNull()).isTrue();
        assertThat(json.path("knowledgeSyllabusVersionId").isNull()).isTrue();
        assertThat(json.path("derivedSubjects").isArray()).isTrue();
        assertThat(json.path("templateVersion").stringValue()).isEqualTo("knowledge_point/1.0");
        assertThat(json.path("status").stringValue()).isEqualTo("uploaded");
        assertThat(json.path("reused").asBoolean()).isFalse();
        assertThat(json.path("createTime").stringValue()).isEqualTo("2026-07-30T10:30:00+08:00");
    }

    @Test
    void serializesCompletedBatchPageAndSnakeCaseTypeCountExactly() throws Exception {
        var row = new CompletedImportBatchVo(
            "10001",
            "系统架构设计师考纲-2026.jsonl",
            "knowledge_point",
            "20001",
            "系统架构设计师 · 2026 考纲",
            "1",
            "管理员",
            "completed",
            OffsetDateTime.parse("2026-07-31T10:32:00+08:00")
        );
        var page = new CompletedImportBatchPageVo(
            List.of(row),
            1,
            new ImportBatchTypeCountsVo(1, 1, 0, 0)
        );

        var json = objectMapper.readTree(objectMapper.writeValueAsBytes(page));

        assertThat(json.size()).isEqualTo(3);
        assertThat(json.path("rows").get(0).size()).isEqualTo(9);
        assertThat(json.path("rows").get(0).path("fileName").stringValue())
            .isEqualTo("系统架构设计师考纲-2026.jsonl");
        assertThat(json.path("rows").get(0).path("completedTime").stringValue())
            .isEqualTo("2026-07-31T10:32:00+08:00");
        assertThat(json.path("total").asLong()).isEqualTo(1L);
        assertThat(json.path("typeCounts").size()).isEqualTo(4);
        assertThat(json.path("typeCounts").path("knowledge_point").asLong()).isEqualTo(1L);
        assertThat(json.path("typeCounts").has("knowledgePoint")).isFalse();
    }

    @Test
    void serializesCompletedBatchDetailFieldsExactly() throws Exception {
        var detail = new CompletedImportBatchDetailVo(
            "10001",
            "系统架构设计师考纲-2026.jsonl",
            "knowledge_point",
            "20001",
            "系统架构设计师 · 2026 考纲",
            "1",
            "管理员",
            "completed",
            OffsetDateTime.parse("2026-07-31T10:32:00+08:00"),
            100,
            95,
            3,
            5
        );

        var json = objectMapper.readTree(objectMapper.writeValueAsBytes(detail));

        assertThat(json.size()).isEqualTo(15);
        assertThat(json.path("documentId").isNull()).isTrue();
        assertThat(json.path("totalCount").asInt()).isEqualTo(100);
        assertThat(json.path("validCount").asInt()).isEqualTo(95);
        assertThat(json.path("warningCount").asInt()).isEqualTo(3);
        assertThat(json.path("failedCount").asInt()).isEqualTo(5);
        assertThat(json.path("generatedCount").asInt()).isZero();
        assertThat(json.has("sourceFilePath")).isFalse();
        assertThat(json.has("traceId")).isFalse();
    }

    @Test
    void serializesFilterOptionsFieldsExactly() throws Exception {
        var options = new ImportBatchFilterOptionsVo(
            List.of(new ImportBatchSyllabusOptionVo("20001", "系统架构设计师 · 2026 考纲")),
            List.of(new ImportBatchUploaderOptionVo("1", "管理员"))
        );

        var json = objectMapper.readTree(objectMapper.writeValueAsBytes(options));

        assertThat(json.size()).isEqualTo(2);
        assertThat(json.path("syllabusVersions").get(0).size()).isEqualTo(2);
        assertThat(json.path("syllabusVersions").get(0).path("id").stringValue()).isEqualTo("20001");
        assertThat(json.path("uploaders").get(0).size()).isEqualTo(2);
        assertThat(json.path("uploaders").get(0).path("name").stringValue()).isEqualTo("管理员");
    }
}
