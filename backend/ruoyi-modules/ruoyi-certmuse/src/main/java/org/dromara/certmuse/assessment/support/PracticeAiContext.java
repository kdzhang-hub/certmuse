package org.dromara.certmuse.assessment.support;

import java.util.List;
import org.dromara.certmuse.ai.domain.AiModelRequest;

/** Fully assembled provider request plus disclosure facts used for output guarding. */
public record PracticeAiContext(
    AiModelRequest request,
    List<String> correctLabels,
    String analysis
) {
}
