package org.dromara.certmuse.catalog.domain.vo;

import org.dromara.common.core.domain.PageResult;

import java.io.Serial;
import java.util.Collection;

/**
 * Completed import batch page with unfiltered visible type counts.
 */
public class CompletedImportBatchPageVo extends PageResult<CompletedImportBatchVo> {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ImportBatchTypeCountsVo typeCounts;

    public CompletedImportBatchPageVo(
        Collection<CompletedImportBatchVo> rows,
        long total,
        ImportBatchTypeCountsVo typeCounts
    ) {
        super(rows, total);
        this.typeCounts = typeCounts;
    }

    public ImportBatchTypeCountsVo getTypeCounts() {
        return typeCounts;
    }
}
