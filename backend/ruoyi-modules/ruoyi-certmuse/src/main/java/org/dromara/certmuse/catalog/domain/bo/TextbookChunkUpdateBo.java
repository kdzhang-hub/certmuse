package org.dromara.certmuse.catalog.domain.bo;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;

@Data
public class TextbookChunkUpdateBo {
    private String heading;
    @NotNull private String content;
    @NotNull private OffsetDateTime updateTime;
    private List<String> knowledgePointIds;
}
