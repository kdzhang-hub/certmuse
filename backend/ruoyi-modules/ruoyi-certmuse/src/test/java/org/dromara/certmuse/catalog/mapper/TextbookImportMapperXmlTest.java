package org.dromara.certmuse.catalog.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.dromara.certmuse.catalog.domain.TextbookChunkInsert;
import org.dromara.certmuse.catalog.domain.TextbookChunkKnowledgeInsert;
import org.dromara.certmuse.catalog.domain.TextbookRecordResultUpdate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class TextbookImportMapperXmlTest {

    private static final String NAMESPACE = "org.dromara.certmuse.catalog.mapper.ImportMapper.";

    @Test
    void mapsDocumentChunkToTextbookAndDerivesSyllabusFromDocument() throws Exception {
        Configuration configuration = configuration();
        Map<String, Object> parameters = completedQueryParameters();
        parameters.put("importType", "textbook");
        String listSql = sql(configuration, "selectCompletedBatches", parameters);

        assertThat(listSql)
            .contains("case when b.import_type = 'document_chunk' then 'textbook'")
            .contains("and b.import_type = 'document_chunk'")
            .contains("coalesce(b.syllabus_version_id, d.syllabus_version_id)")
            .contains("left join cm_document d on d.id = b.document_id and b.import_type = 'document_chunk'")
            .contains("order by b.finished_time desc , b.id desc");

        String countsSql = sql(configuration, "selectCompletedTypeCounts", Map.of("visibleUserId", 7L));
        assertThat(countsSql)
            .contains("count(*) filter (where b.import_type = 'document_chunk') as textbook_count");
    }

    @Test
    void keepsTextbookPreviewPagingStableAndReadsStagingEnvelope() throws Exception {
        Configuration configuration = configuration();
        String recordsSql = sql(
            configuration,
            "selectTextbookPreviewRecords",
            Map.of("batchId", 101L, "limit", 20, "offset", 20)
        );
        String summarySql = sql(configuration, "selectTextbookPreviewSummary", Map.of("batchId", 101L));

        assertThat(recordsSql)
            .contains("where r.batch_id = ?")
            .contains("group by r.id")
            .contains("order by r.source_line_no, r.id")
            .contains("limit ? offset ?");
        assertThat(summarySql)
            .contains("r.raw_record #> '{record,knowledge_points}'")
            .contains("b.import_type = 'document_chunk'");

        String knowledgePointsSql = sql(
            configuration,
            "selectTextbookPreviewKnowledgePointLookups",
            Map.of("batchId", 201L, "examSubjectIds", List.of(11L), "syllabusNumbers", List.of("1.1"))
        );
        assertThat(knowledgePointsSql)
            .contains("join cm_import_batch b on b.id = ?")
            .contains("kp.syllabus_version_id = coalesce(b.syllabus_version_id, d.syllabus_version_id)")
            .contains("kp.syllabus_title");
    }

    @Test
    void bindsReplacementDeletesAndTransactionalPersistenceStatements() throws Exception {
        Configuration configuration = configuration();
        String deleteImages = sql(configuration, "deleteTextbookImages", Map.of("documentId", 201L));
        String deleteRelations = sql(
            configuration, "deleteTextbookKnowledgeRelations", Map.of("documentId", 201L)
        );
        String deleteChunks = sql(configuration, "deleteTextbookChunks", Map.of("documentId", 201L));
        String insertChunks = sql(
            configuration,
            "insertTextbookChunks",
            Map.of("rows", List.of(new TextbookChunkInsert(
                401L, 201L, 1, "第一章", "[\"第一章\"]", "正文", "{\"page_start\":1}", "abc", 7L
            )))
        );
        String insertRelations = sql(
            configuration,
            "insertTextbookKnowledgeRelations",
            Map.of("rows", List.of(new TextbookChunkKnowledgeInsert(501L, 401L, 601L)))
        );
        String updateResults = sql(
            configuration,
            "updateTextbookRecordResults",
            Map.of("rows", List.of(new TextbookRecordResultUpdate(
                701L, "{\"schema_version\":\"import_result/1.0\",\"root_ids\":[\"401\"]}"
            )))
        );
        String submit = sql(configuration, "submitTextbookForAutoPublish", Map.of(
            "id", 201L, "userId", 7L, "confirmedTime", java.time.OffsetDateTime.parse("2026-08-13T10:00:00+08:00")
        ));
        String approve = sql(configuration, "approveTextbookAfterImport", Map.of("id", 201L));
        String publish = sql(configuration, "publishTextbookAfterImport", Map.of("id", 201L));

        assertThat(deleteImages).contains("delete from cm_document_image")
            .contains("select id from cm_document_chunk where document_id = ?");
        assertThat(deleteRelations).contains("delete from cm_chunk_knowledge")
            .contains("select id from cm_document_chunk where document_id = ?");
        assertThat(deleteChunks).contains("delete from cm_document_chunk where document_id = ?");
        assertThat(insertChunks).contains("insert into cm_document_chunk")
            .contains("cast(? as jsonb)")
            .contains("content_hash");
        assertThat(insertRelations).contains("insert into cm_chunk_knowledge");
        assertThat(updateResults).contains("status = 'success'")
            .contains("processed_time = now()")
            .contains("where r.id = v.id and r.status = 'pending'");
        assertThat(submit).contains("status='pending_review'").contains("submitted_by=?")
            .contains("status='draft'");
        assertThat(approve).contains("status='approved'").contains("status='pending_review'")
            .contains("reviewed_by=submitted_by");
        assertThat(publish).contains("status='published'").contains("status='approved'")
            .contains("published_by=reviewed_by");
    }

    @Test
    void resetsPrecheckBatchStateBeforeRetryingItsClearedData() throws Exception {
        Configuration configuration = configuration();

        String sql = sql(configuration, "clearBatchData", Map.of("batchId", 101L));

        assertThat(sql)
            .contains("delete from cm_import_issue where import_batch_id=?")
            .contains("delete from cm_import_record where batch_id=?")
            .contains("update cm_import_batch set status='parsing'")
            .contains("current_stage='read_jsonl'")
            .contains("progress_percent=0")
            .contains("baseline_hash=null")
            .contains("resolution_hash=null")
            .contains("where id=? and status in ('parsing','validating')");
    }

    @Test
    void persistsTheCertificationScopeForPaperImportBatches() throws Exception {
        Configuration configuration = configuration();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id", 101L);
        parameters.put("requestId", "paper-request");
        parameters.put("certificationId", 9L);
        parameters.put("syllabusId", 21L);
        parameters.put("sourceFilePath", "paper/object");
        parameters.put("sourceFileHash", "file-hash");
        parameters.put("sourceFileName", "paper.zip");
        parameters.put("sourceFileSize", 100L);
        parameters.put("sourceFileMime", "application/zip");
        parameters.put("parseConfig", "{}");
        parameters.put("userId", 7L);
        parameters.put("deptId", 8L);
        parameters.put("createTime", java.time.OffsetDateTime.parse("2026-08-21T10:00:00+08:00"));
        String sql = sql(configuration, "insertPaperBatch", parameters);

        assertThat(sql)
            .contains("insert into cm_import_batch(id,request_id,certification_id,syllabus_version_id")
            .contains("values(?,?,?,?");
    }

    private static Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/certmuse/ImportMapper.xml";
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private static Map<String, Object> completedQueryParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("keyword", null);
        parameters.put("importType", null);
        parameters.put("syllabusVersionId", null);
        parameters.put("uploaderId", null);
        parameters.put("completedStartTime", null);
        parameters.put("completedEndTime", null);
        parameters.put("visibleUserId", 7L);
        parameters.put("ascending", false);
        parameters.put("limit", 20);
        parameters.put("offset", 0L);
        return parameters;
    }

    private static String sql(Configuration configuration, String statement, Object parameters) {
        return configuration.getMappedStatement(NAMESPACE + statement)
            .getBoundSql(parameters)
            .getSql()
            .replaceAll("\\s+", " ")
            .trim()
            .toLowerCase();
    }
}
