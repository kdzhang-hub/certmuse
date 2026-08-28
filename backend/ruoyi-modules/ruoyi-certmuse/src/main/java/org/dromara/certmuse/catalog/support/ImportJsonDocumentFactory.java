package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.shared.schema.JsonSchemaVersion;
import org.dromara.certmuse.shared.schema.VersionedJsonDocumentFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * Produces versioned JSON documents required by the import persistence contract.
 */
@Deprecated(forRemoval = true)
public class ImportJsonDocumentFactory extends VersionedJsonDocumentFactory {

    public ImportJsonDocumentFactory(@Qualifier("importStrictJsonMapper") JsonMapper objectMapper) {
        super(objectMapper);
    }
}
