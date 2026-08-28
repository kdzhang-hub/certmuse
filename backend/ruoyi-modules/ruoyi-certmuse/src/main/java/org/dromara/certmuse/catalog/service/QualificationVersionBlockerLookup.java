package org.dromara.certmuse.catalog.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.catalog.domain.ReferenceBlockerRow;
import org.dromara.certmuse.catalog.mapper.QualificationVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Reads M01 delete blockers after a failed write without reusing the aborted transaction. */
@Service
@RequiredArgsConstructor
public class QualificationVersionBlockerLookup {
    private final QualificationVersionMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<ReferenceBlockerRow> qualificationBlockers(long qualificationId) {
        return mapper.selectQualificationBlockers(qualificationId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<ReferenceBlockerRow> versionBlockers(long versionId) {
        return mapper.selectVersionBlockers(versionId);
    }
}
