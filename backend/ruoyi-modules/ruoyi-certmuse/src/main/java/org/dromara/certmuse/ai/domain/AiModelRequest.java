package org.dromara.certmuse.ai.domain;

import java.util.List;

/** Provider-neutral chat request assembled from trusted question snapshots. */
public record AiModelRequest(
    String model,
    List<Message> messages,
    List<Image> images
) {
    public record Message(String role, String content) {
    }

    public record Image(String mediaType, String base64Data) {
    }
}
