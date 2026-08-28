package org.dromara.certmuse.assessment.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Learner message and ephemeral selection sent to the question AI tutor. */
public record SendPracticeAiMessageBo(
    @NotBlank @Size(max = 2000) String message,
    @NotBlank @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    String clientMessageId,
    @NotNull List<@NotBlank @Size(max = 20) String> currentSelection
) {
}
