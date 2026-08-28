package org.dromara.certmuse.catalog.domain.vo;

import java.util.List;

public record TextbookOptionsVo(List<TextbookOptionVo> certifications, List<StatusOptionVo> statuses,
                                List<TextbookOptionVo> creators) {}
