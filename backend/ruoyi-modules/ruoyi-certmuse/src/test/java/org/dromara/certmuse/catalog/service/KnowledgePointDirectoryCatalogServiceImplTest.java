package org.dromara.certmuse.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.dromara.certmuse.catalog.domain.KnowledgePointDirectoryRow;
import org.dromara.certmuse.catalog.mapper.KnowledgePointDirectoryCatalogMapper;
import org.dromara.certmuse.catalog.service.impl.KnowledgePointDirectoryCatalogServiceImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@Tag("dev")
class KnowledgePointDirectoryCatalogServiceImplTest {

    @Test
    void distinguishesMissingCurrentGoalFromAnEmptyVisibleDirectory() {
        KnowledgePointDirectoryCatalogMapper mapper = Mockito.mock(KnowledgePointDirectoryCatalogMapper.class);
        KnowledgePointDirectoryCatalogService service = new KnowledgePointDirectoryCatalogServiceImpl(mapper);
        List<Long> ids = List.of(1L);
        when(mapper.selectForCurrentGoal(7L, ids)).thenReturn(List.of(
            new KnowledgePointDirectoryRow(false, null, null, null, null, null)));

        var missingGoal = service.lookupForCurrentGoal(7L, ids);

        when(mapper.selectForCurrentGoal(7L, ids)).thenReturn(List.of(
            new KnowledgePointDirectoryRow(true, null, null, null, null, null)));
        var emptyDirectory = service.lookupForCurrentGoal(7L, ids);

        assertThat(missingGoal.currentGoalFound()).isFalse();
        assertThat(missingGoal.items()).isEmpty();
        assertThat(emptyDirectory.currentGoalFound()).isTrue();
        assertThat(emptyDirectory.items()).isEmpty();
        verify(mapper, org.mockito.Mockito.times(2)).selectForCurrentGoal(7L, ids);
    }

    @Test
    void removesTheCurrentGoalMarkerRowFromVisibleItems() {
        KnowledgePointDirectoryCatalogMapper mapper = Mockito.mock(KnowledgePointDirectoryCatalogMapper.class);
        KnowledgePointDirectoryCatalogService service = new KnowledgePointDirectoryCatalogServiceImpl(mapper);
        List<Long> ids = List.of(1L);
        KnowledgePointDirectoryRow marker = new KnowledgePointDirectoryRow(true, null, null, null, null, null);
        KnowledgePointDirectoryRow item = new KnowledgePointDirectoryRow(true, 1L, "1.1", "标题", 2L, "科目");
        when(mapper.selectForCurrentGoal(7L, ids)).thenReturn(List.of(marker, item));

        var result = service.lookupForCurrentGoal(7L, ids);

        assertThat(result.currentGoalFound()).isTrue();
        assertThat(result.items()).containsExactly(item);
    }
}
