package org.dromara.certmuse.learning.service.impl;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.learning.mapper.MistakeFactMapper;
import org.dromara.certmuse.learning.service.MistakeFactRecorder;
import org.springframework.stereotype.Service;

/** Deduplicates one error fact for each frozen attempt/leaf pair. */
@Service @RequiredArgsConstructor
public class MistakeFactRecorderImpl implements MistakeFactRecorder {
    private final MistakeFactMapper mapper;
    @Override public void record(long userId, long goalId, long attemptId, long knowledgePointId, String evidenceGroupKey) {
        mapper.insert(IdUtil.getSnowflakeNextId(), userId, goalId, attemptId, knowledgePointId, evidenceGroupKey);
    }
}
