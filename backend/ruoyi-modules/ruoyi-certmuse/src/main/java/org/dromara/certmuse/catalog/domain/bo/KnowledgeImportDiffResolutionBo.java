package org.dromara.certmuse.catalog.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** One manual decision for a generated knowledge import difference. */
@Data
public class KnowledgeImportDiffResolutionBo {
    private String diffId;
    @NotBlank
    private String decision;
    private String oldKnowledgePointId;
}
