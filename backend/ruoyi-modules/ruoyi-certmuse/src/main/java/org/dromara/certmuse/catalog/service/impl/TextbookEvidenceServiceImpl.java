package org.dromara.certmuse.catalog.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.ai.client.EmbeddingClient;
import org.dromara.certmuse.ai.client.EmbeddingException;
import org.dromara.certmuse.ai.config.CertMuseAiProperties;
import org.dromara.certmuse.catalog.domain.TextbookEvidence;
import org.dromara.certmuse.catalog.domain.TextbookEvidenceCandidateRow;
import org.dromara.certmuse.catalog.domain.TextbookEvidenceQuery;
import org.dromara.certmuse.catalog.domain.TextbookKnowledgeScopeRow;
import org.dromara.certmuse.catalog.mapper.TextbookEvidenceMapper;
import org.dromara.certmuse.catalog.service.TextbookEvidenceService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Hybrid pg_trgm/pgvector retrieval with deterministic reciprocal-rank fusion. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextbookEvidenceServiceImpl implements TextbookEvidenceService {
    private static final BigDecimal RRF_K = BigDecimal.valueOf(60);
    private final TextbookEvidenceMapper mapper;
    private final EmbeddingClient embeddingClient;
    private final CertMuseAiProperties properties;
    private final JsonMapper jsonMapper;

    @Override
    public List<TextbookEvidence> retrieve(TextbookEvidenceQuery query) {
        if (!properties.isRagEnabled() || query.knowledgePointIds().isEmpty() || query.queryText().isBlank()) {
            return List.of();
        }
        TextbookKnowledgeScopeRow scope = mapper.selectKnowledgeScope(query.knowledgePointIds());
        if (scope == null) return List.of();
        List<TextbookEvidenceCandidateRow> lexical = lexical(query, scope, true);
        List<TextbookEvidenceCandidateRow> semantic = semantic(query, scope, true);
        if (lexical.isEmpty() && semantic.isEmpty()) {
            lexical = lexical(query, scope, false);
            semantic = semantic(query, scope, false);
        }
        return fuse(lexical, semantic);
    }

    @Override
    public void queueDocument(long documentId) {
        if (embeddingConfigured()) {
            mapper.queueDocumentEmbeddings(documentId, properties.getEmbeddingModel(), properties.getEmbeddingDimensions());
        }
    }

    @Override
    public void queueChunk(long chunkId) {
        if (embeddingConfigured()) {
            mapper.queueChunkEmbedding(chunkId, properties.getEmbeddingModel(), properties.getEmbeddingDimensions());
        }
    }

    private List<TextbookEvidenceCandidateRow> lexical(TextbookEvidenceQuery query,
                                                        TextbookKnowledgeScopeRow scope, boolean strict) {
        return mapper.selectLexicalCandidates(query.knowledgePointIds(), scope.getSyllabusVersionId(),
            scope.getExamSubjectId(), strict, query.queryText(), properties.getRetrievalCandidateLimit());
    }

    private List<TextbookEvidenceCandidateRow> semantic(TextbookEvidenceQuery query,
                                                         TextbookKnowledgeScopeRow scope, boolean strict) {
        if (!embeddingConfigured()) return List.of();
        try {
            String vector = vector(embeddingClient.embed(query.queryText()));
            return mapper.selectSemanticCandidates(query.knowledgePointIds(), scope.getSyllabusVersionId(),
                scope.getExamSubjectId(), strict, vector, properties.getEmbeddingModel(),
                properties.getRetrievalCandidateLimit());
        } catch (EmbeddingException exception) {
            log.warn("Textbook semantic retrieval degraded, errorCode={}", exception.getErrorCode());
            return List.of();
        }
    }

    private List<TextbookEvidence> fuse(List<TextbookEvidenceCandidateRow> lexical,
                                        List<TextbookEvidenceCandidateRow> semantic) {
        Map<Long, Ranked> values = new LinkedHashMap<>();
        add(values, lexical, true);
        add(values, semantic, false);
        List<Ranked> ranked = new ArrayList<>(values.values());
        ranked.sort((left, right) -> right.fused.compareTo(left.fused));
        List<TextbookEvidence> result = new ArrayList<>();
        int characters = 0;
        for (Ranked value : ranked) {
            if (result.size() >= properties.getRetrievalEvidenceLimit()) break;
            int remaining = properties.getRetrievalMaxCharacters() - characters;
            if (remaining <= 0) break;
            String content = value.row.getContent();
            if (content.length() > remaining) content = content.substring(0, remaining);
            result.add(evidence(value, content));
            characters += content.length();
        }
        return List.copyOf(result);
    }

    private void add(Map<Long, Ranked> values, List<TextbookEvidenceCandidateRow> candidates, boolean lexical) {
        for (int index = 0; index < candidates.size(); index++) {
            TextbookEvidenceCandidateRow row = candidates.get(index);
            Ranked ranked = values.computeIfAbsent(row.getChunkId(), ignored -> new Ranked(row));
            BigDecimal contribution = BigDecimal.ONE.divide(RRF_K.add(BigDecimal.valueOf(index + 1)),
                12, RoundingMode.HALF_UP);
            ranked.fused = ranked.fused.add(contribution);
            if (lexical) ranked.lexical = row.getScore();
            else ranked.semantic = row.getScore();
        }
    }

    private TextbookEvidence evidence(Ranked value, String content) {
        try {
            JsonNode headings = jsonMapper.readTree(value.row.getHeadingPath()).path("headings");
            List<String> headingPath = headings.valueStream().map(JsonNode::asText).toList();
            JsonNode source = jsonMapper.readTree(value.row.getSourceLocator());
            return new TextbookEvidence(value.row.getChunkId(), value.row.getTextbookId(),
                value.row.getTextbookTitle(), value.row.getEdition(), headingPath,
                integer(source, "page_start", "source_page_start"),
                integer(source, "page_end", "source_page_end"), content, value.row.getContentHash(),
                value.lexical, value.semantic, value.fused);
        } catch (Exception exception) {
            throw new IllegalStateException("Textbook evidence metadata is invalid", exception);
        }
    }

    private boolean embeddingConfigured() {
        return properties.isRagEnabled() && properties.getEmbeddingModel() != null
            && !properties.getEmbeddingModel().isBlank();
    }

    public static String vector(List<Double> values) {
        return values.toString();
    }

    private static Integer integer(JsonNode node, String... fields) {
        for (String field : fields) if (!node.path(field).isMissingNode() && !node.path(field).isNull()) return node.path(field).asInt();
        return null;
    }

    private static final class Ranked {
        private final TextbookEvidenceCandidateRow row;
        private BigDecimal lexical;
        private BigDecimal semantic;
        private BigDecimal fused = BigDecimal.ZERO;
        private Ranked(TextbookEvidenceCandidateRow row) { this.row = row; }
    }
}
