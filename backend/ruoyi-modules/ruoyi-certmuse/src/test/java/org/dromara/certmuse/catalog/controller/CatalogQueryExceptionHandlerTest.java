package org.dromara.certmuse.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.dromara.certmuse.catalog.support.CatalogQueryException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class CatalogQueryExceptionHandlerTest {

    @Test
    void preservesTheKnowledgeTreeImportConflictResponse() {
        var response = new CatalogQueryExceptionHandler().handle(new CatalogQueryException(
            409,
            "KNOWLEDGE_TREE_IMPORT_IN_PROGRESS",
            "存在未完成的知识点导入，无法删除知识点树"
        ));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(409);
        assertThat(response.getBody().getData().errorCode()).isEqualTo("KNOWLEDGE_TREE_IMPORT_IN_PROGRESS");
    }
}
