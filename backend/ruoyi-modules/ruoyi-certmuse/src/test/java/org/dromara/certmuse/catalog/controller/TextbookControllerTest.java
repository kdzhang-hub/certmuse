package org.dromara.certmuse.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cn.dev33.satoken.annotation.SaCheckPermission;
import java.lang.reflect.Method;
import org.dromara.certmuse.catalog.domain.bo.TextbookChunkQueryBo;
import org.dromara.certmuse.catalog.domain.bo.TextbookChunkUpdateBo;
import org.dromara.certmuse.catalog.domain.bo.TextbookQueryBo;
import org.dromara.certmuse.catalog.domain.bo.TextbookUpdateBo;
import org.dromara.certmuse.catalog.service.TextbookService;
import org.dromara.common.log.annotation.Log;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class TextbookControllerTest {
    @Test
    void exposesAllElevenM03RoutesWithFrozenPermissions() throws Exception {
        new TextbookController(mock(TextbookService.class));
        assertPermission("list", "certmuse:catalog:resource:list", TextbookQueryBo.class);
        assertPermission("options", "certmuse:catalog:resource:list");
        assertPermission("detail", "certmuse:catalog:resource:query", String.class);
        assertPermission("update", "certmuse:catalog:resource:edit", String.class, TextbookUpdateBo.class);
        assertPermission("publish", "certmuse:catalog:resource:edit", String.class);
        assertPermission("offline", "certmuse:catalog:resource:edit", String.class);
        assertPermission("chunks", "certmuse:catalog:resource:query", String.class, TextbookChunkQueryBo.class);
        assertPermission("chunk", "certmuse:catalog:resource:query", String.class, String.class);
        assertPermission("update", "certmuse:catalog:resource:edit", String.class, String.class, TextbookChunkUpdateBo.class);
        assertPermission("deleteChunks", "certmuse:catalog:resource:remove", String.class, java.util.List.class);
        assertPermission("delete", "certmuse:catalog:resource:remove", String.class);
    }

    @Test
    void doesNotAuditTextbookChunkContent() throws Exception {
        Method method = TextbookController.class.getMethod("update", String.class, String.class,
            TextbookChunkUpdateBo.class);

        Log log = method.getAnnotation(Log.class);

        assertThat(log.isSaveRequestData()).isFalse();
        assertThat(log.isSaveResponseData()).isFalse();
    }

    @Test
    void delegatesAllTextbookAndChunkOperationsToTheService() {
        TextbookService service = mock(TextbookService.class);
        TextbookController controller = new TextbookController(service);
        TextbookQueryBo query = mock(TextbookQueryBo.class);
        TextbookChunkQueryBo chunkQuery = mock(TextbookChunkQueryBo.class);
        TextbookChunkUpdateBo update = mock(TextbookChunkUpdateBo.class);
        TextbookUpdateBo textbookUpdate = mock(TextbookUpdateBo.class);

        controller.list(query);
        controller.options();
        controller.detail("book-1");
        controller.update("book-1", textbookUpdate);
        controller.publish("book-1");
        controller.offline("book-1");
        controller.chunks("book-1", chunkQuery);
        controller.chunk("book-1", "chunk-1");
        controller.update("book-1", "chunk-1", update);
        controller.deleteChunks("book-1", java.util.List.of("chunk-1", "chunk-2"));
        controller.delete("book-1");

        org.mockito.Mockito.verify(service).list(query);
        org.mockito.Mockito.verify(service).options();
        org.mockito.Mockito.verify(service).detail("book-1");
        org.mockito.Mockito.verify(service).updateTextbook("book-1", textbookUpdate);
        org.mockito.Mockito.verify(service).publishTextbook("book-1");
        org.mockito.Mockito.verify(service).takeTextbookOffline("book-1");
        org.mockito.Mockito.verify(service).chunks("book-1", chunkQuery);
        org.mockito.Mockito.verify(service).chunkDetail("book-1", "chunk-1");
        org.mockito.Mockito.verify(service).updateChunk("book-1", "chunk-1", update);
        org.mockito.Mockito.verify(service).deleteChunks("book-1", java.util.List.of("chunk-1", "chunk-2"));
        org.mockito.Mockito.verify(service).deleteTextbook("book-1");
    }

    private static void assertPermission(String name, String permission, Class<?>... parameters) throws Exception {
        Method method = TextbookController.class.getMethod(name, parameters);
        assertThat(method.getAnnotation(SaCheckPermission.class).value()).containsExactly(permission);
    }
}
