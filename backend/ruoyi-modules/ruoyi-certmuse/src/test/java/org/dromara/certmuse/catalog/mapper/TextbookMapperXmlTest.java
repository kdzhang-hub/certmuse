package org.dromara.certmuse.catalog.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.dromara.certmuse.catalog.domain.bo.TextbookQueryBo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class TextbookMapperXmlTest {
    @Test
    void parsesM03StatementsAndKeepsQueriesControlledAndScoped() throws Exception {
        Configuration configuration = configuration();
        String namespace = "org.dromara.certmuse.catalog.mapper.TextbookMapper.";
        for (String statement : java.util.List.of("selectTextbooks", "countTextbooks", "selectTextbook",
            "selectActiveCertification", "countTextbookKnowledgeMappings", "updateTextbook", "publishTextbook", "takeTextbookOffline", "selectChunks", "countChunks", "selectChunk", "updateChunk", "countKnowledgePoints", "deleteChunkKnowledgeByChunk", "insertChunkKnowledge", "deleteImages",
            "deleteChunkKnowledge", "deleteChunks", "softDeleteTextbook")) {
            assertThat(configuration.hasStatement(namespace + statement)).isTrue();
        }

        TextbookQueryBo query = new TextbookQueryBo();
        query.setOrderByColumn("chunkCount"); query.setIsAsc("asc");
        Map<String, Object> list = new HashMap<>();
        list.put("query", query); list.put("certificationId", 3L); list.put("syllabusVersionId", null); list.put("createBy", null);
        list.put("visibleUserId", 7L); list.put("limit", 20); list.put("offset", 0L);
        String listSql = sql(configuration, namespace + "selectTextbooks", list);
        assertThat(listSql).contains("d.document_type = 'textbook'").contains("d.del_flag = '0'")
            .contains("d.create_by = ?").contains("d.certification_id = ?").contains("coalesce(stats.chunk_count, 0) asc")
            .contains("left join lateral").doesNotContain("${");

        String certificationOptionsSql = sql(configuration, namespace + "selectCertificationOptions", Map.of("visibleUserId", 7L));
        assertThat(certificationOptionsSql).contains("from cm_exam_certification ec")
            .contains("ec.status = '0'").doesNotContain("cm_document").doesNotContain("create_by");

        Map<String, Object> chunks = new HashMap<>();
        chunks.put("documentId", 1L); chunks.put("knowledgePointId", 2L); chunks.put("examSubjectId", null);
        chunks.put("includeDescendants", true); chunks.put("hasKnowledgePoint", false); chunks.put("keyword", "架构"); chunks.put("limit", 20); chunks.put("offset", 0L);
        String chunkSql = sql(configuration, namespace + "selectChunks", chunks);
        assertThat(chunkSql).contains("with recursive descendants").contains("c.document_id = ?")
            .contains("jsonb_agg").contains("not exists").contains("order by c.chunk_order asc");

        String updateSql = sql(configuration, namespace + "updateChunk", Map.of(
            "documentId", 1L, "chunkId", 2L, "updateTime", java.time.OffsetDateTime.now(),
            "heading", "标题", "content", "正文", "contentHash", "hash", "userId", 7L));
        assertThat(updateSql).contains("update_time = ?").contains("content_hash = ?")
            .doesNotContain("heading_path").doesNotContain("source_locator");

        String textbookUpdateSql = sql(configuration, namespace + "updateTextbook", Map.of(
            "id", 1L, "title", "教材", "certificationId", 2L, "clearSyllabusVersion", true, "userId", 7L));
        assertThat(textbookUpdateSql).contains("update cm_document").contains("certification_id = ?")
            .contains("syllabus_version_id = case when ? then null").contains("update_time = now()")
            .contains("document_type = 'textbook'").contains("del_flag = '0'");

        assertThat(sql(configuration, namespace + "publishTextbook", Map.of("id", 1L, "userId", 7L)))
            .contains("set status = 'published'").contains("published_by = ?").contains("published_time = now()")
            .contains("status = 'draft'");
        assertThat(sql(configuration, namespace + "takeTextbookOffline", Map.of("id", 1L, "userId", 7L)))
            .contains("set status = 'draft'").contains("published_by = null").contains("published_time = null")
            .contains("status = 'published'");

        assertThat(sql(configuration, namespace + "countTextbookKnowledgeMappings", Map.of("documentId", 1L)))
            .contains("from cm_chunk_knowledge ck").contains("join cm_document_chunk chunk")
            .contains("chunk.document_id = ?");

        assertThat(sql(configuration, namespace + "deleteImages", Map.of("documentId", 1L, "ids", java.util.List.of(2L))))
            .startsWith("delete from cm_document_image");
        assertThat(sql(configuration, namespace + "deleteChunkKnowledge", Map.of("documentId", 1L, "ids", java.util.List.of(2L))))
            .startsWith("delete from cm_chunk_knowledge");
        assertThat(sql(configuration, namespace + "deleteChunkKnowledgeByChunk", Map.of("chunkId", 2L)))
            .startsWith("delete from cm_chunk_knowledge");
        assertThat(sql(configuration, namespace + "softDeleteTextbook", Map.of("id", 1L, "userId", 7L)))
            .contains("set del_flag = '1'").doesNotContain("delete from cm_document");
    }

    private static Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/certmuse/TextbookMapper.xml";
        try (InputStream stream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }
    private static String sql(Configuration configuration, String statement, Object parameters) {
        return configuration.getMappedStatement(statement).getBoundSql(parameters).getSql()
            .replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
