package org.dromara.certmuse.ai.client;

import java.util.List;

/** Generates a semantic vector for trusted textbook content or a retrieval query. */
public interface EmbeddingClient {
    List<Double> embed(String input);
}
