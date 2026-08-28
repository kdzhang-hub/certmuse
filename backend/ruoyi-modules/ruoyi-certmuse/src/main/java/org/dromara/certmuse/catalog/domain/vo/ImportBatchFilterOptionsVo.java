package org.dromara.certmuse.catalog.domain.vo;

import java.util.List;

/**
 * Filters available for the completed import batch list.
 */
public record ImportBatchFilterOptionsVo(
    List<ImportBatchSyllabusOptionVo> syllabusVersions,
    List<ImportBatchUploaderOptionVo> uploaders
) {
}
