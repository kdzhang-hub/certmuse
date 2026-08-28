package org.dromara.certmuse.catalog.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Explicit batch of manual knowledge import decisions. */
@Data
public class KnowledgeImportDiffBatchResolutionBo {
    @Valid
    @NotEmpty
    @Size(max = 500)
    private List<KnowledgeImportDiffResolutionBo> resolutions;
}
