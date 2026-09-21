package org.dromara.certmuse.catalog.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.dromara.certmuse.catalog.domain.KnowledgePointInsert;
import org.dromara.certmuse.catalog.domain.KnowledgePointUpdate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class KnowledgeTreeMapperXmlTest {
    @Test
    void parsesAllKnowledgeTreeStatements() throws Exception {
        var configuration = new Configuration();
        String resource = "mapper/certmuse/KnowledgeTreeMapper.xml";
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments()).parse();
        }

        assertThat(configuration.hasStatement(
            "org.dromara.certmuse.catalog.mapper.KnowledgeTreeMapper.selectNodes"))
            .isTrue();
        assertThat(configuration.hasStatement(
            "org.dromara.certmuse.catalog.mapper.KnowledgeTreeMapper.lockSyllabusVersion"))
            .isTrue();
        assertThat(configuration.hasStatement(
            "org.dromara.certmuse.catalog.mapper.KnowledgeTreeMapper.insertIdempotency"))
            .isTrue();
        assertThat(configuration.hasStatement(
            "org.dromara.certmuse.catalog.mapper.KnowledgeTreeMapper.countImportingImports"))
            .isTrue();
        assertThat(configuration.hasStatement(
            "org.dromara.certmuse.catalog.mapper.KnowledgeTreeMapper.deleteQuestionsExclusiveToSyllabusVersion"))
            .isTrue();
        assertThat(configuration.hasStatement(
            "org.dromara.certmuse.catalog.mapper.KnowledgeTreeMapper.deleteSyllabusVersion"))
            .isTrue();

        String importResource = "mapper/certmuse/ImportMapper.xml";
        try (InputStream inputStream = Resources.getResourceAsStream(importResource)) {
            new XMLMapperBuilder(inputStream, configuration, importResource, configuration.getSqlFragments()).parse();
        }
        assertThat(configuration.hasStatement(
            "org.dromara.certmuse.catalog.mapper.ImportMapper.acceptConfirmation"))
            .isTrue();
        assertThat(configuration.hasStatement(
            "org.dromara.certmuse.catalog.mapper.ImportMapper.selectByIdForUpdate"))
            .isTrue();
        assertThat(configuration.hasStatement(
            "org.dromara.certmuse.catalog.mapper.ImportMapper.enqueueJobAt"))
            .isTrue();

        String deleteSql = configuration.getMappedStatement(
            "org.dromara.certmuse.catalog.mapper.KnowledgeTreeMapper.deleteSyllabusVersion")
            .getBoundSql(Map.of("syllabusVersionId", 21L)).getSql();
        assertThat(normalizeSql(deleteSql)).contains("delete from cm_syllabus_version")
            .contains("where id = ?");

        String importingSql = configuration.getMappedStatement(
            "org.dromara.certmuse.catalog.mapper.KnowledgeTreeMapper.countImportingImports")
            .getBoundSql(Map.of("syllabusVersionId", 21L)).getSql();
        assertThat(normalizeSql(importingSql)).contains("status = 'importing'")
            .doesNotContain("import_type = 'knowledge_point'");

        Map<String, Object> idempotencyParameters = new HashMap<>();
        idempotencyParameters.put("id", 1L);
        idempotencyParameters.put("action", "syllabus_published_date_update");
        idempotencyParameters.put("requestId", "adf7bbf7-7f3f-4632-9e06-78b6d79b047d");
        idempotencyParameters.put("payloadHash", "hash");
        idempotencyParameters.put("resourceId", 21L);
        idempotencyParameters.put("expiresTime", OffsetDateTime.parse("2026-08-14T09:00:00+08:00"));
        String idempotencySql = normalizeSql(configuration.getMappedStatement(
            "org.dromara.certmuse.catalog.mapper.KnowledgeTreeMapper.insertIdempotency")
            .getBoundSql(idempotencyParameters).getSql());
        assertThat(idempotencySql).contains("insert into cm_idempotency_record")
            .contains("'processing'").contains("'syllabus_version'")
            .contains("on conflict (action_code, request_id) do nothing");

        String questionDeleteSql = configuration.getMappedStatement(
            "org.dromara.certmuse.catalog.mapper.KnowledgeTreeMapper.deleteQuestionsExclusiveToSyllabusVersion")
            .getBoundSql(Map.of("syllabusVersionId", 21L)).getSql();
        assertThat(normalizeSql(questionDeleteSql))
            .contains("delete from cm_question question")
            .contains("not exists")
            .contains("other_knowledge_point.syllabus_version_id <> ?");

        String sourceObjectSql = configuration.getMappedStatement(
            "org.dromara.certmuse.catalog.mapper.KnowledgeTreeMapper.selectSourceObjectKeysBySyllabusVersion")
            .getBoundSql(Map.of("syllabusVersionId", 21L)).getSql();
        assertThat(normalizeSql(sourceObjectSql))
            .contains("from cm_syllabus_version")
            .contains("from cm_document where syllabus_version_id = ?")
            .contains("from cm_import_batch batch");

        Map<String, Object> confirmationParameters = new HashMap<>();
        confirmationParameters.put("id", 101L);
        confirmationParameters.put("userId", 7L);
        confirmationParameters.put("confirmedTime", null);
        String confirmationSql = configuration.getMappedStatement(
            "org.dromara.certmuse.catalog.mapper.ImportMapper.acceptConfirmation")
            .getBoundSql(confirmationParameters).getSql();
        assertThat(normalizeSql(confirmationSql))
            .contains("b.failed_count=0")
            .contains("b.baseline_hash is not null")
            .contains("d.resolution_status='pending'")
            .doesNotContain("cm_knowledge_point kp");

        String countSql = configuration.getMappedStatement(
            "org.dromara.certmuse.catalog.mapper.ImportMapper.countKnowledgePoints")
            .getBoundSql(Map.of("syllabusVersionId", 21L)).getSql();
        assertThat(normalizeSql(countSql)).contains("del_flag = '0'");

        Map<String, Object> knowledgePointParameters = new HashMap<>();
        knowledgePointParameters.put("rows", List.of(new KnowledgePointInsert(
            1L, 21L, 31L, null, "1.1", "标题", 2, 1, null, 2, "0", 7L, 8L
        )));
        String insertKnowledgePointSql = normalizeSql(configuration.getMappedStatement(
            "org.dromara.certmuse.catalog.mapper.ImportMapper.insertKnowledgePoints")
            .getBoundSql(knowledgePointParameters).getSql());
        assertThat(insertKnowledgePointSql)
            .contains("diagnostic_enabled, recommendation_enabled")
            .contains("false, true");

        knowledgePointParameters.put("rows", List.of(new KnowledgePointUpdate(
            1L, null, "1.1", "标题", 2, 1, null, 2, "0", 7L
        )));
        String updateKnowledgePointSql = normalizeSql(configuration.getMappedStatement(
            "org.dromara.certmuse.catalog.mapper.ImportMapper.updateKnowledgePoints")
            .getBoundSql(knowledgePointParameters).getSql());
        assertThat(updateKnowledgePointSql).contains("recommendation_enabled=true");
    }

    @Test
    void completedBatchQueriesEnforceStatusPublicTypesVisibilityAndStablePaging() throws Exception {
        var configuration = new Configuration();
        String resource = "mapper/certmuse/ImportMapper.xml";
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        String namespace = "org.dromara.certmuse.catalog.mapper.ImportMapper.";
        assertThat(configuration.hasStatement(namespace + "selectCompletedBatches")).isTrue();
        assertThat(configuration.hasStatement(namespace + "countCompletedBatches")).isTrue();
        assertThat(configuration.hasStatement(namespace + "selectCompletedTypeCounts")).isTrue();
        assertThat(configuration.hasStatement(namespace + "selectCompletedSyllabusOptions")).isTrue();
        assertThat(configuration.hasStatement(namespace + "selectCompletedUploaderOptions")).isTrue();
        assertThat(configuration.hasStatement(namespace + "selectCompletedBatchDetail")).isTrue();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("keyword", "101");
        parameters.put("importType", "knowledge_point");
        parameters.put("syllabusVersionId", 21L);
        parameters.put("uploaderId", 7L);
        parameters.put("completedStartTime", null);
        parameters.put("completedEndTime", null);
        parameters.put("visibleUserId", 7L);
        parameters.put("ascending", false);
        parameters.put("limit", 20);
        parameters.put("offset", 0L);
        String listSql = normalizeSql(configuration.getMappedStatement(namespace + "selectCompletedBatches")
            .getBoundSql(parameters).getSql());

        assertThat(listSql)
            .contains("b.status = 'completed'")
            .contains("b.import_type in ('knowledge_point', 'question', 'document_chunk')")
            .contains("b.create_by = ?")
            .contains("b.source_file_name ilike")
            .contains("left join sys_user")
            .contains("order by b.finished_time desc , b.id desc")
            .contains("limit ? offset ?")
            .doesNotContain("source_file_path")
            .doesNotContain("source_file_hash")
            .doesNotContain("trace_id");

        String countsSql = normalizeSql(configuration.getMappedStatement(namespace + "selectCompletedTypeCounts")
            .getBoundSql(Map.of("visibleUserId", 7L)).getSql());
        assertThat(countsSql)
            .contains("filter (where b.import_type = 'knowledge_point')")
            .doesNotContain("source_file_name ilike")
            .doesNotContain("syllabus_version_id = ?");

        String detailSql = normalizeSql(configuration.getMappedStatement(namespace + "selectCompletedBatchDetail")
            .getBoundSql(Map.of("id", 101L, "visibleUserId", 7L)).getSql());
        assertThat(detailSql)
            .contains("b.status = 'completed'")
            .contains("b.create_by = ?")
            .contains("b.id = ?");
    }

    @Test
    void completedBatchListUsesTheDeclaredOptionalSyllabusParameter() throws Exception {
        var configuration = new Configuration();
        String resource = "mapper/certmuse/ImportMapper.xml";
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        Method method = ImportMapper.class.getMethod(
            "selectCompletedBatches", String.class, String.class, Long.class, Long.class,
            OffsetDateTime.class, OffsetDateTime.class, Long.class, boolean.class, int.class, long.class);
        var resolver = new ParamNameResolver(configuration, method);
        String statement = "org.dromara.certmuse.catalog.mapper.ImportMapper.selectCompletedBatches";

        Object withoutSyllabus = resolver.getNamedParams(new Object[] {
            null, null, null, null, null, null, null, false, 20, 0L
        });
        String unfilteredSql = normalizeSql(configuration.getMappedStatement(statement)
            .getBoundSql(withoutSyllabus).getSql());
        assertThat(unfilteredSql).doesNotContain("coalesce(b.syllabus_version_id, d.syllabus_version_id) = ?");

        Object withSyllabus = resolver.getNamedParams(new Object[] {
            null, null, 21L, null, null, null, null, false, 20, 0L
        });
        String filteredSql = normalizeSql(configuration.getMappedStatement(statement)
            .getBoundSql(withSyllabus).getSql());
        assertThat(filteredSql).contains("coalesce(b.syllabus_version_id, d.syllabus_version_id) = ?");
    }

    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
