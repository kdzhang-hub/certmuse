package org.dromara.certmuse.shared;

import cn.hutool.crypto.digest.DigestUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Creates the import duplicate fingerprint for a question's visible content.
 */
public final class QuestionSemanticHasher {

    private QuestionSemanticHasher() {
    }

    /**
     * Builds a stable fingerprint that ignores formatting-only text differences and choice ordering.
     */
    public static String hash(String questionType, String stem, Collection<String> optionTexts) {
        StringBuilder canonical = new StringBuilder()
            .append(questionType == null ? "" : questionType)
            .append('\n')
            .append(normalize(stem))
            .append('\n');
        if ("CHOICE".equals(questionType)) {
            List<String> normalizedOptions = new ArrayList<>();
            if (optionTexts != null) {
                optionTexts.stream().map(QuestionSemanticHasher::normalize).sorted().forEach(normalizedOptions::add);
            }
            canonical.append(String.join("\n", normalizedOptions));
        }
        return DigestUtil.sha256Hex(canonical.toString());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
