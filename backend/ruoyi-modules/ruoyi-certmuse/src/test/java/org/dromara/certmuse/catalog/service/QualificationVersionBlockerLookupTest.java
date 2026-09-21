package org.dromara.certmuse.catalog.service;

import java.util.List;
import org.dromara.certmuse.catalog.domain.ReferenceBlockerRow;
import org.dromara.certmuse.catalog.mapper.QualificationVersionMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class QualificationVersionBlockerLookupTest {

    @Mock
    private QualificationVersionMapper mapper;

    @Test
    void readsQualificationAndVersionBlockersThroughTheFreshReadMapper() {
        var qualificationBlockers = List.of(new ReferenceBlockerRow("textbook", "教材", 2));
        var versionBlockers = List.of(new ReferenceBlockerRow("question", "题目", 5));
        when(mapper.selectQualificationBlockers(10L)).thenReturn(qualificationBlockers);
        when(mapper.selectVersionBlockers(20L)).thenReturn(versionBlockers);

        var lookup = new QualificationVersionBlockerLookup(mapper);

        assertThat(lookup.qualificationBlockers(10L)).containsExactlyElementsOf(qualificationBlockers);
        assertThat(lookup.versionBlockers(20L)).containsExactlyElementsOf(versionBlockers);
        verify(mapper).selectQualificationBlockers(10L);
        verify(mapper).selectVersionBlockers(20L);
    }
}
