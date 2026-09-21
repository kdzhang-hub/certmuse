package org.dromara.certmuse.learning.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.dromara.certmuse.catalog.domain.KnowledgePointDirectoryRow;
import org.dromara.certmuse.catalog.service.KnowledgePointDirectoryCatalogService;
import org.dromara.certmuse.learning.support.KnowledgePointDirectoryException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@Tag("dev")
class KnowledgePointDirectoryServiceImplTest {

    @Test
    void normalizesDuplicatesAndPreservesFirstInputOrder() {
        KnowledgePointDirectoryCatalogService catalog = Mockito.mock(KnowledgePointDirectoryCatalogService.class);
        when(catalog.lookupForCurrentGoal(7L, List.of(2L, 1L))).thenReturn(new KnowledgePointDirectoryCatalogService.LookupResult(
            true, List.of(row(1L, "1.1"), row(2L, "1.2"))));

        var result = new KnowledgePointDirectoryServiceImpl(catalog).lookup(7L, "002,1,2,001");

        assertThat(result.items()).extracting(item -> item.id()).containsExactly("2", "1");
        verify(catalog).lookupForCurrentGoal(7L, List.of(2L, 1L));
    }

    @Test
    void silentlyOmitsUnmatchedIds() {
        KnowledgePointDirectoryCatalogService catalog = Mockito.mock(KnowledgePointDirectoryCatalogService.class);
        when(catalog.lookupForCurrentGoal(7L, List.of(1L, 2L))).thenReturn(new KnowledgePointDirectoryCatalogService.LookupResult(
            true, List.of(row(2L, "1.2"))));

        var result = new KnowledgePointDirectoryServiceImpl(catalog).lookup(7L, "1,2");

        assertThat(result.items()).extracting(item -> item.id()).containsExactly("2");
    }

    @Test
    void returnsGoalNotActiveWithoutLeakingIds() {
        KnowledgePointDirectoryCatalogService catalog = Mockito.mock(KnowledgePointDirectoryCatalogService.class);
        when(catalog.lookupForCurrentGoal(7L, List.of(1L))).thenReturn(new KnowledgePointDirectoryCatalogService.LookupResult(false, List.of()));

        assertThatThrownBy(() -> new KnowledgePointDirectoryServiceImpl(catalog).lookup(7L, "1"))
            .isInstanceOfSatisfying(KnowledgePointDirectoryException.class, exception -> {
                assertThat(exception.status()).isEqualTo(409);
                assertThat(exception.data().errorCode()).isEqualTo("LEARNING_GOAL_NOT_ACTIVE");
                assertThat(exception.data().fieldErrors()).isEmpty();
            });
    }

    @Test
    void rejectsInvalidAndOutOfRangeIdsBeforeCatalogAccess() {
        KnowledgePointDirectoryCatalogService catalog = Mockito.mock(KnowledgePointDirectoryCatalogService.class);
        KnowledgePointDirectoryServiceImpl service = new KnowledgePointDirectoryServiceImpl(catalog);

        assertInvalid(service, "1,,2", "INVALID_FORMAT");
        assertInvalid(service, " 1", "INVALID_FORMAT");
        assertInvalid(service, "", "REQUIRED");
        assertInvalid(service, "1,abc", "INVALID_FORMAT");
        assertInvalid(service, "-1", "INVALID_FORMAT");
        assertInvalid(service, "1.5", "INVALID_FORMAT");
        assertInvalid(service, "1e3", "INVALID_FORMAT");
        assertInvalid(service, "１", "INVALID_FORMAT");
        assertInvalid(service, "0", "OUT_OF_RANGE");
        assertInvalid(service, "9223372036854775808", "OUT_OF_RANGE");
        assertInvalid(service, String.join(",", java.util.stream.LongStream.rangeClosed(1, 101)
            .mapToObj(Long::toString).toList()), "OUT_OF_RANGE");
        Mockito.verifyNoInteractions(catalog);
    }

    @Test
    void acceptsMaximumLongAndExactlyOneHundredNormalizedIds() {
        KnowledgePointDirectoryCatalogService catalog = Mockito.mock(KnowledgePointDirectoryCatalogService.class);
        List<Long> ids = java.util.stream.LongStream.rangeClosed(1, 99).boxed()
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        ids.add(Long.MAX_VALUE);
        when(catalog.lookupForCurrentGoal(7L, ids)).thenReturn(new KnowledgePointDirectoryCatalogService.LookupResult(true, List.of()));

        var result = new KnowledgePointDirectoryServiceImpl(catalog).lookup(7L,
            java.util.stream.Stream.concat(java.util.stream.LongStream.rangeClosed(1, 99).mapToObj(Long::toString),
                java.util.stream.Stream.of("9223372036854775807")).collect(java.util.stream.Collectors.joining(",")));

        assertThat(result.items()).isEmpty();
        verify(catalog).lookupForCurrentGoal(7L, ids);
    }

    @Test
    void translatesCatalogFailureToSafeRetryableFailure() {
        KnowledgePointDirectoryCatalogService catalog = Mockito.mock(KnowledgePointDirectoryCatalogService.class);
        when(catalog.lookupForCurrentGoal(7L, List.of(1L))).thenThrow(new IllegalStateException("internal detail"));

        assertThatThrownBy(() -> new KnowledgePointDirectoryServiceImpl(catalog).lookup(7L, "1"))
            .isInstanceOfSatisfying(KnowledgePointDirectoryException.class, exception -> {
                assertThat(exception.status()).isEqualTo(500);
                assertThat(exception.data().errorCode()).isEqualTo("KNOWLEDGE_POINT_LOOKUP_SYSTEM_FAILURE");
                assertThat(exception.data().retryable()).isTrue();
                assertThat(exception.data().fieldErrors()).isEmpty();
                assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
            });
    }

    private void assertInvalid(KnowledgePointDirectoryServiceImpl service, String ids, String fieldCode) {
        assertThatThrownBy(() -> service.lookup(7L, ids))
            .isInstanceOfSatisfying(KnowledgePointDirectoryException.class, exception -> {
                assertThat(exception.status()).isEqualTo(400);
                assertThat(exception.data().errorCode()).isEqualTo("KNOWLEDGE_POINT_LOOKUP_INVALID");
                assertThat(exception.data().fieldErrors()).singleElement().satisfies(error -> {
                    assertThat(error.field()).isEqualTo("ids");
                    assertThat(error.code()).isEqualTo(fieldCode);
                });
            });
    }

    private KnowledgePointDirectoryRow row(long id, String number) {
        return new KnowledgePointDirectoryRow(true, id, number, "标题" + id, 10L, "综合知识");
    }
}
