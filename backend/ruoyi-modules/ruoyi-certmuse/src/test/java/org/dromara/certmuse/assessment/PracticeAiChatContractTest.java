package org.dromara.certmuse.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.dromara.certmuse.assessment.controller.PracticeAiChatController;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class PracticeAiChatContractTest {
    @Test
    void controllerUsesTheFrozenAndPermission() {
        SaCheckPermission permission = PracticeAiChatController.class.getAnnotation(SaCheckPermission.class);
        assertThat(permission.mode()).isEqualTo(SaMode.AND);
        assertThat(permission.value()).containsExactly(
            "certmuse:student", "certmuse:assessment:knowledge-practice:ai-chat");
    }

    @Test
    void migrationDefinesConversationMessageAndPermissionGuards() throws Exception {
        String migration = Files.readString(find(
            "script/sql/postgres/certmuse/20260818_add-practice-ai-chat.sql"));
        String manifest = Files.readString(find("script/sql/postgres/certmuse/local-migration-manifest.txt"));
        assertThat(migration).contains(
            "CREATE TABLE cm_ai_conversation",
            "CREATE TABLE cm_ai_message",
            "uk_cm_ai_conversation_item",
            "uk_cm_ai_message_client_id",
            "CREATE_PRACTICE_AI_CONVERSATION",
            "CANCEL_PRACTICE_AI_GENERATION",
            "certmuse:assessment:knowledge-practice:ai-chat");
        assertThat(manifest).contains("20260818_add-practice-ai-chat.sql");
    }

    @Test
    void mapperParsesAndScopesQuestionConversationAndMessageReads() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/certmuse/PracticeAiChatMapper.xml";
        try (InputStream stream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        String namespace = "org.dromara.certmuse.assessment.mapper.PracticeAiChatMapper.";
        assertThat(configuration.hasStatement(namespace + "selectQuestionContext")).isTrue();
        assertThat(configuration.hasStatement(namespace + "lockOwnedConversation")).isTrue();
        assertThat(configuration.hasStatement(namespace + "insertUserMessage")).isTrue();
        assertThat(configuration.hasStatement(namespace + "cancelAssistantMessage")).isTrue();

        String ownedSql = normalize(configuration.getMappedStatement(namespace + "selectOwnedConversation")
            .getBoundSql(Map.of("conversationId", 3L, "userId", 9L)).getSql());
        String contextSql = normalize(configuration.getMappedStatement(namespace + "selectQuestionContext")
            .getBoundSql(Map.of("sessionId", 5L, "userId", 9L, "questionOrder", 2)).getSql());
        assertThat(ownedSql).contains("c.id=? and c.user_id=?");
        assertThat(contextSql).contains("s.id=? and s.user_id=?", "s.session_type='self_practice'",
            "sq.presentation_snapshot::text", "sq.grading_snapshot::text");
    }

    @Test
    void idempotencyReplayBodiesIncludeTheRequiredSchemaVersion() throws Exception {
        String service = Files.readString(find(
            "ruoyi-modules/ruoyi-certmuse/src/main/java/org/dromara/certmuse/assessment/service/impl/PracticeAiChatServiceImpl.java"));
        assertThat(service).contains(
            "AssessmentJsonSchema.PRACTICE_AI_CREATE_RESPONSE.version()",
            "AssessmentJsonSchema.PRACTICE_AI_CANCEL_RESPONSE.version()");
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private static Path find(String relative) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.exists(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("file not found: " + relative);
    }
}
