package org.dromara.certmuse.learning.domain.vo;

import java.io.Serial;
import java.util.Collection;
import org.dromara.common.core.domain.PageResult;

/** Task page plus the unfiltered supplement-button fact. */
public class LearningTaskPageVo extends PageResult<LearningTaskListItemVo> {
    @Serial
    private static final long serialVersionUID = 1L;
    private final boolean canShowSupplement;

    public LearningTaskPageVo(Collection<LearningTaskListItemVo> rows, long total, boolean canShowSupplement) {
        super(rows, total);
        this.canShowSupplement = canShowSupplement;
    }

    public boolean isCanShowSupplement() {
        return canShowSupplement;
    }
}
